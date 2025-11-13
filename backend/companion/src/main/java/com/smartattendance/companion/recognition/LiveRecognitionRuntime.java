package com.smartattendance.companion.recognition;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartattendance.companion.CompanionSettings;
import com.smartattendance.companion.SessionState;
import com.smartattendance.config.AttendanceProperties;
import com.smartattendance.util.OpenCVLoader;
import com.smartattendance.util.OpenCVUtils;
import com.smartattendance.vision.HaarFaceDetector;
import com.smartattendance.vision.Recognizer;
import com.smartattendance.vision.preprocess.ImageQuality;
import com.smartattendance.vision.preprocess.FaceImageProcessor;
import com.smartattendance.vision.recognizer.LBPHRecognizer;
import com.smartattendance.vision.tracking.FaceTrack;
import com.smartattendance.vision.tracking.FaceTrackGroup;

import org.apache.commons.lang3.StringUtils;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Coordinates webcam capture, face detection, recognition, and attendance submission.
 */
public final class LiveRecognitionRuntime implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(LiveRecognitionRuntime.class);

    private final SessionState state;
    private final AttendanceProperties config;
    private final CompanionSettings settings;
    private final RecognitionEventBus eventBus;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String companionToken;
    private final Instant scheduledStart;
    private final Instant scheduledEnd;
    private final int lateThresholdMinutes;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "companion-recognition-loop");
        thread.setDaemon(true);
        return thread;
    });
    private final ExecutorService attendanceExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "companion-attendance-dispatcher");
        thread.setDaemon(true);
        return thread;
    });

    private volatile FrameSource frameSource;
    private SessionWindow window;
    private HaarFaceDetector detector;
    private Recognizer recognizer;
    private FaceTrackGroup trackGroup;
    private final Map<String, TrackedFace> trackedFaces = new ConcurrentHashMap<>();
    private final Set<String> recordedStudents = ConcurrentHashMap.newKeySet();
    private final Set<String> missingStudents;
    private final Map<String, String> studentNames;
    private final Map<String, SessionWindow.RosterEntry> rosterEntries = new ConcurrentHashMap<>();
    private final List<String> rosterOrder = Collections.synchronizedList(new ArrayList<>());
    private Consumer<RecognitionEvent> windowEventListener;
    private Runnable stopSessionAction;

    private double autoAcceptMaxDistance;
    private double manualReviewMaxDistance;
    private int minFrames;
    private double minMotion;
    private int minFace;
    private int attemptIntervalMs;
    private double blurThreshold;
    private int maxManualPrompts = 3;

    public LiveRecognitionRuntime(SessionState state,
                                  AttendanceProperties config,
                                  CompanionSettings settings,
                                  RecognitionEventBus eventBus) {
        this.state = state;
        this.config = config;
        this.settings = settings;
        this.eventBus = eventBus;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.missingStudents = Set.copyOf(state.missingStudentIds());
        this.studentNames = state.labelMap();
        this.companionToken = state.companionToken();
        this.scheduledStart = state.scheduledStart();
        this.scheduledEnd = state.scheduledEnd();
        this.lateThresholdMinutes = state.lateThresholdMinutes();
    }

    public void setStopSessionAction(Runnable stopSessionAction) {
        this.stopSessionAction = stopSessionAction;
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        if (!OpenCVLoader.loadOrWarn()) {
            log.warn("OpenCV not available; recognition runtime disabled");
            running.set(false);
            return;
        }
        configureThresholds();
        try {
            detector = new HaarFaceDetector(resolveCascadePath(), config);
            recognizer = loadRecognizer();
            trackGroup = new FaceTrackGroup(TimeUnit.SECONDS.toMillis(4));
            frameSource = openFrameSource();
            window = new SessionWindow(frameSource);
            window.open();
            window.setManualMarkListener(this::handleManualRosterMark);
            window.setEndSessionListener(this::handleEndSessionRequest);
            windowEventListener = window::appendEvent;
            eventBus.subscribe(windowEventListener);
            eventBus.publish(new RecognitionEvent(
                    RecognitionEventType.CAMERA_STARTED,
                    Instant.now(),
                    null,
                    null,
                    null,
                    Double.NaN,
                    "Camera started",
                    true,
                    false));
            loadInitialRoster();
            executor.submit(this::loop);
        } catch (Exception ex) {
            log.error("Failed to start recognition runtime: {}", ex.getMessage(), ex);
            if (frameSource != null) {
                frameSource.close();
                frameSource = null;
            }
            running.set(false);
        }
    }

    private void configureThresholds() {
        AttendanceProperties.Recognition recognition = config.recognition();
        if (recognition != null) {
            autoAcceptMaxDistance = recognition.autoAcceptMaxDistance();
            manualReviewMaxDistance = recognition.manualReviewMaxDistance();
        } else {
            autoAcceptMaxDistance = 35.0;
            manualReviewMaxDistance = 55.0;
        }
        if (manualReviewMaxDistance < autoAcceptMaxDistance) {
            manualReviewMaxDistance = autoAcceptMaxDistance;
        }
        AttendanceProperties.Live live = config.live();
        if (live != null && live.recognition() != null) {
            AttendanceProperties.LiveRecognition lr = live.recognition();
            minFrames = Math.max(3, lr.minFrames());
            minMotion = Math.max(20.0, lr.motionThreshold());
            minFace = Math.max(120, lr.minFace());
            attemptIntervalMs = Math.max(750, lr.intervalMs());
        } else {
            minFrames = 6;
            minMotion = 60.0;
            minFace = 160;
            attemptIntervalMs = 1500;
        }
        blurThreshold = live != null ? live.blurVarianceThreshold() : 80.0;
        if (!Double.isFinite(blurThreshold) || blurThreshold <= 0.0d) {
            blurThreshold = 80.0;
        }
    }

    private FrameSource openFrameSource() {
        AttendanceProperties.Camera camera = config.camera();
        int index = camera != null ? camera.index() : 0;
        double fps = camera != null ? camera.fps() : 30.0d;
        if (isArm64Mac()) {
            return new OpenCvFrameSource(index, fps);
        }
        return new WebcamFrameSource(index);
    }

    private String resolveCascadePath() {
        Path cascade = state.cascadePath();
        if (cascade != null && Files.exists(cascade)) {
            return cascade.toAbsolutePath().toString();
        }
        Path fallback = config.directories().modelDir().resolve("haarcascade_frontalface_default.xml");
        return Files.exists(fallback) ? fallback.toAbsolutePath().toString() : null;
    }

    private boolean isArm64Mac() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        return os.contains("mac") && (arch.contains("arm64") || arch.contains("aarch64"));
    }

    private Recognizer loadRecognizer() throws IOException {
        LBPHRecognizer lbph = new LBPHRecognizer(new FaceImageProcessor(config.preprocessing())).configureFrom(config);
        Path modelPath = state.modelPath();
        if (modelPath == null) {
            throw new IOException("Model path missing from session state");
        }
        Path modelDir = modelPath.getParent();
        if (modelDir == null) {
            modelDir = modelPath.getParent();
        }
        if (modelDir == null) {
            modelDir = modelPath.getParent();
        }
        if (modelDir == null) {
            modelDir = state.sessionDirectory();
        }
        lbph.loadModel(modelDir);
        return lbph;
    }

    private void loop() {
        long frameIntervalMs = Math.max(20L, Math.round(1000.0 / Math.max(15.0, config.camera().fps())));
        while (running.get()) {
            try {
                BufferedImage image = frameSource != null ? frameSource.getImage() : null;
                if (image == null) {
                    continue;
                }
                if (window != null) {
                    window.updateFrame(image);
                }
                processFrame(image);
                Thread.sleep(frameIntervalMs);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception ex) {
                log.warn("Recognition loop error: {}", ex.getMessage(), ex);
                eventBus.publish(new RecognitionEvent(
                        RecognitionEventType.ERROR,
                        Instant.now(),
                        null,
                        null,
                        null,
                        Double.NaN,
                        ex.getMessage(),
                        false,
                        false));
            }
        }
    }

    private void processFrame(BufferedImage image) {
        Mat mat = OpenCVUtils.bufferedImageToMat(image);
        if (mat.empty()) {
            return;
        }
        try {
            double variance = ImageQuality.laplacianVariance(mat);
            if (variance < blurThreshold) {
                eventBus.publish(new RecognitionEvent(
                        RecognitionEventType.FRAME_PROCESSED,
                        Instant.now(),
                        null,
                        null,
                        null,
                        variance,
                        "Frame rejected: too blurry",
                        false,
                        false));
                return;
            }
            List<Rectangle> detections = detector.detect(mat);
            List<Rectangle> filteredDetections = new ArrayList<>(detections.size());
            for (Rectangle detection : detections) {
                if (isLikelyFace(detection, mat.width(), mat.height())) {
                    filteredDetections.add(detection);
                }
            }
            trackGroup.update(filteredDetections);
            reconcileTrackedFaces();
            List<TrackedFace> snapshot = new ArrayList<>(trackedFaces.values());
            for (TrackedFace tracked : snapshot) {
                evaluateTrack(tracked, mat);
            }
            window.updateTrackedFaces(snapshot);
        } finally {
            try { mat.release(); } catch (Exception ignored) {}
        }
    }

    private void reconcileTrackedFaces() {
        int warmupFrames = Math.max(2, Math.min(minFrames, 5));
        Set<String> liveTrackIds = new HashSet<>();
        for (FaceTrack track : trackGroup.getTracks()) {
            if (!track.wasUpdatedThisFrame()) {
                continue;
            }
            liveTrackIds.add(track.getId());
            if (!trackedFaces.containsKey(track.getId()) && track.getSeenFrames() < warmupFrames) {
                continue;
            }
            trackedFaces.computeIfAbsent(track.getId(), id -> {
                TrackedFace face = new TrackedFace(track);
                face.setOverlayColor(Color.YELLOW);
                eventBus.publish(new RecognitionEvent(
                        RecognitionEventType.FACE_DETECTED,
                        Instant.now(),
                        id,
                        null,
                        null,
                        Double.NaN,
                        "Face detected",
                        true,
                        false));
                return face;
            });
        }
        trackedFaces.keySet().removeIf(id -> !liveTrackIds.contains(id));
    }

    private void evaluateTrack(TrackedFace tracked, Mat mat) {
        FaceTrack track = tracked.track();
        if (track == null || !track.wasUpdatedThisFrame() || track.getMatchBounds() == null) {
            return;
        }
        Rectangle bounds = track.getMatchBounds();
        if (bounds.width < minFace || bounds.height < minFace) {
            return;
        }
        if (track.getSeenFrames() < minFrames) {
            return;
        }
        if (track.getMotionAccum() < minMotion) {
            return;
        }
        if (Duration.between(tracked.lastAttempt(), Instant.now()).toMillis() < attemptIntervalMs) {
            return;
        }
        Rect roiRect = clamp(bounds, mat.width(), mat.height());
        if (roiRect.width <= 0 || roiRect.height <= 0) {
            return;
        }
        Mat face = new Mat(mat, roiRect);
        try {
            Imgproc.resize(face, face, new Size(200, 200));
            Recognizer.Prediction prediction = recognizer.recognize(face);
            if (prediction == null) {
                return;
            }
            String predictedId = prediction.id();
            double distance = prediction.confidence();
            tracked.setLastConfidence(distance);
            tracked.markAttempt(Instant.now());

            if (!StringUtils.isNotBlank(predictedId) || "unknown".equalsIgnoreCase(predictedId)) {
                handleUnknown(tracked, distance);
                return;
            }
            if (missingStudents.contains(predictedId)) {
                handleUnknown(tracked, distance);
                return;
            }
            tracked.setStudentId(predictedId);
            String friendlyName = studentNames.getOrDefault(predictedId, predictedId);
            tracked.setStudentName(friendlyName);

            if (distance <= autoAcceptMaxDistance) {
                tracked.setState(FaceTrackState.AUTO_ACCEPTED);
                tracked.setOverlayColor(new Color(16, 158, 72));
                autoMarkAttendance(tracked, friendlyName, distance);
            } else if (distance <= manualReviewMaxDistance) {
                tracked.setState(FaceTrackState.MANUAL_REVIEW);
                tracked.setOverlayColor(new Color(199, 128, 27));
                requestManualConfirmation(tracked, friendlyName, distance);
            } else {
                handleUnknown(tracked, distance);
            }
        } finally {
            try { face.release(); } catch (Exception ignored) {}
        }
    }

    private void handleUnknown(TrackedFace tracked, double distance) {
        tracked.setState(FaceTrackState.IGNORED);
        tracked.setOverlayColor(new Color(170, 50, 50));
        eventBus.publish(new RecognitionEvent(
                RecognitionEventType.AUTO_REJECTED,
                Instant.now(),
                tracked.track().getId(),
                null,
                null,
                distance,
                "Low confidence",
                false,
                false));
    }

    private void autoMarkAttendance(TrackedFace tracked, String friendlyName, double distance) {
        if (!recordedStudents.add(tracked.studentId())) {
            markAttendanceAlreadyRecorded(tracked, friendlyName, distance, false);
            return;
        }
        submitAttendance(tracked.studentId(), distance, false, "Automatic recognition")
                .thenAccept(record -> {
                    if (record != null) {
                        tracked.setOverlayColor(new Color(16, 158, 72));
                        tracked.setState(FaceTrackState.COMPLETED);
                        updateRosterFromRecord(record);
                        eventBus.publish(new RecognitionEvent(
                                RecognitionEventType.ATTENDANCE_RECORDED,
                                Instant.now(),
                                tracked.track().getId(),
                                tracked.studentId(),
                                friendlyName,
                                distance,
                                "Marked present",
                                true,
                                false));
                    } else {
                        recordedStudents.remove(tracked.studentId());
                        tracked.setOverlayColor(new Color(170, 50, 50));
                        tracked.setState(FaceTrackState.IGNORED);
                    }
                });
    }

    private void requestManualConfirmation(TrackedFace tracked, String friendlyName, double distance) {
        String studentId = tracked.studentId();
        if (studentId == null || studentId.isBlank()) {
            return;
        }
        if (recordedStudents.contains(studentId)) {
            markAttendanceAlreadyRecorded(tracked, friendlyName, distance, true);
            return;
        }
        if (tracked.manualPrompted()) {
            return;
        }
        if (tracked.manualPromptAttempts() >= maxManualPrompts) {
            tracked.setState(FaceTrackState.MANUAL_REJECTED);
            tracked.setOverlayColor(new Color(170, 50, 50));
            eventBus.publish(new RecognitionEvent(
                    RecognitionEventType.MANUAL_REJECTED,
                    Instant.now(),
                    tracked.track() != null ? tracked.track().getId() : null,
                    studentId,
                    friendlyName,
                    distance,
                    "Manual confirmation limit reached",
                    false,
                    true));
            return;
        }
        tracked.setManualPrompted(true);
        boolean confirmed = window.promptManualConfirmation(friendlyName);
        if (confirmed) {
            if (!recordedStudents.add(studentId)) {
                markAttendanceAlreadyRecorded(tracked, friendlyName, distance, true);
                return;
            }
            tracked.incrementManualPromptAttempts();
            tracked.setState(FaceTrackState.MANUAL_ACCEPTED);
            tracked.setOverlayColor(new Color(16, 158, 72));
            submitAttendance(studentId, distance, true, "Manual confirmation from companion")
                    .thenAccept(record -> {
                        if (record != null) {
                            updateRosterFromRecord(record);
                            eventBus.publish(new RecognitionEvent(
                                    RecognitionEventType.MANUAL_CONFIRMED,
                                    Instant.now(),
                                    tracked.track() != null ? tracked.track().getId() : null,
                                    studentId,
                                    friendlyName,
                                    distance,
                                    "Manual confirmation accepted",
                                    true,
                                    true));
                        } else {
                            recordedStudents.remove(studentId);
                            handleManualSubmissionFailure(tracked, friendlyName, distance);
                        }
                    });
        } else {
            tracked.setManualPrompted(false);
            tracked.setState(FaceTrackState.MANUAL_REJECTED);
            tracked.setOverlayColor(new Color(170, 50, 50));
            eventBus.publish(new RecognitionEvent(
                    RecognitionEventType.MANUAL_REJECTED,
                    Instant.now(),
                    tracked.track() != null ? tracked.track().getId() : null,
                    studentId,
                    friendlyName,
                    distance,
                    "Manual confirmation rejected",
                    false,
                    true));
        }
    }

    private void handleManualSubmissionFailure(TrackedFace tracked, String friendlyName, double distance) {
        if (tracked.manualPromptAttempts() >= maxManualPrompts) {
            tracked.setManualPrompted(false);
            tracked.setState(FaceTrackState.MANUAL_REJECTED);
            tracked.setOverlayColor(new Color(170, 50, 50));
            eventBus.publish(new RecognitionEvent(
                    RecognitionEventType.MANUAL_REJECTED,
                    Instant.now(),
                    tracked.track() != null ? tracked.track().getId() : null,
                    tracked.studentId(),
                    friendlyName,
                    distance,
                    "Manual confirmation failed after maximum retries",
                    false,
                    true));
            return;
        }
        tracked.setManualPrompted(false);
        tracked.setState(FaceTrackState.MANUAL_REVIEW);
        tracked.setOverlayColor(new Color(199, 128, 27));
        eventBus.publish(new RecognitionEvent(
                RecognitionEventType.MANUAL_CONFIRMATION_REQUIRED,
                Instant.now(),
                tracked.track() != null ? tracked.track().getId() : null,
                tracked.studentId(),
                friendlyName,
                distance,
                "Manual confirmation failed, please retry",
                false,
                true));
    }

    private void markAttendanceAlreadyRecorded(TrackedFace tracked, String friendlyName, double distance, boolean manualContext) {
        String displayName = (friendlyName != null && !friendlyName.isBlank())
                ? friendlyName
                : tracked.studentName();
        tracked.setOverlayColor(new Color(36, 128, 36));
        tracked.setState(FaceTrackState.COMPLETED);
        eventBus.publish(new RecognitionEvent(
                RecognitionEventType.ATTENDANCE_SKIPPED,
                Instant.now(),
                tracked.track() != null ? tracked.track().getId() : null,
                tracked.studentId(),
                displayName,
                distance,
                "Already recorded",
                true,
                manualContext));
    }

    private void handleManualRosterMark(SessionWindow.RosterAction action) {
        if (action == null || action.entry() == null) {
            return;
        }
        SessionWindow.RosterEntry entry = action.entry();
        String studentId = entry.studentId();
        if (studentId == null || studentId.isBlank()) {
            return;
        }
        boolean resetToAbsent = action.resetToAbsent();
        window.setRosterSubmissionInProgress(studentId, true);
        Double submissionConfidence = entry.confidence() != null && Double.isFinite(entry.confidence())
                ? entry.confidence()
                : null;
        boolean removedForReset = false;
        if (resetToAbsent) {
            removedForReset = recordedStudents.remove(studentId);
        } else {
            recordedStudents.add(studentId);
        }
        submitAttendance(studentId,
                submissionConfidence,
                true,
                resetToAbsent ? "Manual roster reset to absent" : "Manual roster mark",
                resetToAbsent ? "absent" : null)
                .whenComplete((record, error) -> {
                    try {
                        if (error != null) {
                            log.warn("Manual roster submission failed for {}: {}", studentId, error.getMessage());
                            if (resetToAbsent && removedForReset) {
                                recordedStudents.add(studentId);
                            }
                            if (!resetToAbsent) {
                                recordedStudents.remove(studentId);
                            }
                            return;
                        }
                        if (record != null) {
                            updateRosterFromRecord(record);
                            String friendlyName = firstNonBlank(record.studentName(), entry.fullName(),
                                    studentNames.getOrDefault(studentId, studentId));
                            String message = resetToAbsent
                                    ? "Manual roster reset to absent"
                                    : "Manual roster mark recorded";
                            eventBus.publish(new RecognitionEvent(
                                    RecognitionEventType.MANUAL_CONFIRMED,
                                    Instant.now(),
                                    null,
                                    studentId,
                                    friendlyName,
                                    record.confidence() != null ? record.confidence() : Double.NaN,
                                    message,
                                    true,
                                    true));
                        } else {
                            if (resetToAbsent && removedForReset) {
                                recordedStudents.add(studentId);
                            }
                            if (!resetToAbsent) {
                                recordedStudents.remove(studentId);
                            }
                        }
                    } finally {
                        window.setRosterSubmissionInProgress(studentId, false);
                    }
                });
    }

    private void handleEndSessionRequest() {
        if (window != null) {
            window.setEndSessionEnabled(false);
        }
        Thread stopper = new Thread(() -> {
            try {
                notifyBackendStop();
            } catch (Exception ex) {
                log.warn("Failed to notify backend about session stop: {}", ex.getMessage());
            } finally {
                if (stopSessionAction != null) {
                    stopSessionAction.run();
                }
            }
        }, "companion-end-session");
        stopper.setDaemon(true);
        stopper.start();
    }

    private void notifyBackendStop() throws Exception {
        String baseUrl = settings.backendBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            return;
        }
        String sessionId = state.sessionId();
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/sessions/" + sessionId + "/stop"))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"action\":\"stop\"}"));
        String serviceToken = settings.serviceToken() != null ? settings.serviceToken().trim() : "";
        String resolvedCompanionToken = companionToken != null ? companionToken.trim() : "";
        if (!serviceToken.isBlank()) {
            builder.header("Authorization", "Bearer " + serviceToken);
        } else if (!resolvedCompanionToken.isBlank()) {
            builder.header("Authorization", "Bearer " + resolvedCompanionToken);
            builder.header("X-Companion-Token", resolvedCompanionToken);
        }
        httpClient.send(builder.build(), HttpResponse.BodyHandlers.discarding());
    }

    private CompletableFuture<AttendanceRecordView> submitAttendance(String studentId,
                                                                     double confidence,
                                                                     boolean manual,
                                                                     String notes) {
        Double resolvedConfidence = Double.isFinite(confidence) ? confidence : null;
        return submitAttendance(studentId, resolvedConfidence, manual, notes, null);
    }

    private CompletableFuture<AttendanceRecordView> submitAttendance(String studentId,
                                                                     Double confidence,
                                                                     boolean manual,
                                                                     String notes,
                                                                     String statusOverride) {
        final String resolvedCompanionToken = companionToken != null ? companionToken.trim() : "";
        final String resolvedServiceToken = settings.serviceToken() != null ? settings.serviceToken().trim() : "";
        final String sessionId = state.sessionId() != null ? state.sessionId().trim() : "";
        final String sectionId = state.sectionId() != null ? state.sectionId().trim() : "";
        final boolean useCompanionEndpoint = resolvedServiceToken.isBlank()
                && !resolvedCompanionToken.isBlank()
                && !sessionId.isBlank()
                && !sectionId.isBlank();
        final String targetUrl = useCompanionEndpoint
                ? settings.backendBaseUrl() + "/companion/sections/" + sectionId + "/sessions/" + sessionId
                        + "/attendance"
                : settings.backendBaseUrl() + "/attendance";

        return CompletableFuture.supplyAsync(() -> {
            try {
                ObjectNode payload = objectMapper.createObjectNode();
                payload.put("sessionId", state.sessionId());
                payload.put("studentId", studentId);
                String status = statusOverride != null && !statusOverride.isBlank()
                        ? statusOverride
                        : determineAttendanceStatus(Instant.now());
                payload.put("status", status);
                if (confidence != null && Double.isFinite(confidence)) {
                    payload.put("confidenceScore", confidence);
                }
                payload.put("markingMethod", manual ? "manual" : "auto");
                if (notes != null && !notes.isBlank()) {
                    payload.put("notes", notes);
                }

                log.info("Submitting attendance payload: {}", payload);

                HttpRequest.Builder builder = HttpRequest.newBuilder()
                        .uri(URI.create(targetUrl))
                        .timeout(Duration.ofSeconds(5))
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload.toString()));
                if (!resolvedServiceToken.isBlank()) {
                    builder.header("Authorization", "Bearer " + resolvedServiceToken);
                } else if (!resolvedCompanionToken.isBlank()) {
                    builder.header("Authorization", "Bearer " + resolvedCompanionToken);
                    if (useCompanionEndpoint) {
                        builder.header("X-Companion-Token", resolvedCompanionToken);
                    }
                } else {
                    log.warn("Attendance submission missing authorization token; request will likely fail");
                }
                HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                log.info("Attendance submission response (status={}): {}", response.statusCode(), response.body());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return parseAttendanceRecord(response.body(), studentId, status, manual, confidence);
                }
                log.warn("Attendance submission failed: HTTP {} - {}", response.statusCode(), response.body());
                eventBus.publish(new RecognitionEvent(
                        RecognitionEventType.ERROR,
                        Instant.now(),
                        null,
                        studentId,
                        studentNames.getOrDefault(studentId, studentId),
                        confidence != null && Double.isFinite(confidence) ? confidence : Double.NaN,
                        "Attendance API rejected request",
                        false,
                        manual));
            } catch (Exception ex) {
                log.warn("Attendance submission error: {}", ex.getMessage());
                eventBus.publish(new RecognitionEvent(
                        RecognitionEventType.ERROR,
                        Instant.now(),
                        null,
                        studentId,
                        studentNames.getOrDefault(studentId, studentId),
                        confidence != null && Double.isFinite(confidence) ? confidence : Double.NaN,
                        ex.getMessage(),
                        false,
                        manual));
            }
            return null;
        }, attendanceExecutor);
    }

    private void loadInitialRoster() {
        attendanceExecutor.submit(() -> {
            List<AttendanceRecordView> records = fetchRosterFromBackend();
            if (records.isEmpty()) {
                window.updateRoster(List.of());
                return;
            }
            List<SessionWindow.RosterEntry> entries = new ArrayList<>();
            for (AttendanceRecordView record : records) {
                if (record == null || record.studentId() == null || record.studentId().isBlank()) {
                    continue;
                }
                String studentId = record.studentId();
                String name = firstNonBlank(record.studentName(), studentNames.getOrDefault(studentId, studentId));
                SessionWindow.RosterEntry entry = new SessionWindow.RosterEntry(
                        studentId,
                        name,
                        record.studentNumber(),
                        firstNonBlank(record.status(), "pending"),
                        record.markedAt(),
                        record.markingMethod(),
                        record.confidence());
                entries.add(entry);
                rosterEntries.put(studentId, entry);
                if ("present".equalsIgnoreCase(entry.status()) || "late".equalsIgnoreCase(entry.status())) {
                    recordedStudents.add(studentId);
                }
            }
            synchronized (rosterOrder) {
                rosterOrder.clear();
                for (SessionWindow.RosterEntry entry : entries) {
                    rosterOrder.add(entry.studentId());
                }
            }
            window.updateRoster(entries);
        });
    }

    private List<AttendanceRecordView> fetchRosterFromBackend() {
        String baseUrl = settings.backendBaseUrl();
        String sessionId = state.sessionId();
        String sectionId = state.sectionId();
        if (baseUrl == null || baseUrl.isBlank()
                || sessionId == null || sectionId == null
                || sessionId.isBlank() || sectionId.isBlank()) {
            return List.of();
        }
        try {
            String url = baseUrl + "/companion/sections/" + sectionId + "/sessions/" + sessionId + "/roster";
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .header("Accept", "application/json")
                    .GET();
            String serviceToken = settings.serviceToken() != null ? settings.serviceToken().trim() : "";
            String resolvedCompanionToken = companionToken != null ? companionToken.trim() : "";
            if (!serviceToken.isBlank()) {
                builder.header("Authorization", "Bearer " + serviceToken);
            } else if (!resolvedCompanionToken.isBlank()) {
                builder.header("Authorization", "Bearer " + resolvedCompanionToken);
                builder.header("X-Companion-Token", resolvedCompanionToken);
            } else {
                log.warn("Roster fetch missing authorization token; request may fail");
            }
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                String body = response.body();
                if (body == null || body.isBlank()) {
                    return List.of();
                }
                JsonNode root = objectMapper.readTree(body);
                if (!root.isArray()) {
                    return List.of();
                }
                List<AttendanceRecordView> result = new ArrayList<>();
                for (JsonNode node : root) {
                    AttendanceRecordView record = parseRosterNode(node);
                    if (record != null) {
                        result.add(record);
                    }
                }
                return result;
            }
            log.warn("Roster fetch failed: HTTP {} - {}", response.statusCode(), response.body());
        } catch (Exception ex) {
            log.warn("Failed to load roster: {}", ex.getMessage());
        }
        return List.of();
    }

    private AttendanceRecordView parseRosterNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String studentId = safeText(node.get("studentId"));
        String status = safeText(node.get("status"));
        String method = safeText(node.get("markingMethod"));
        Double confidence = node.hasNonNull("confidenceScore") ? node.get("confidenceScore").asDouble() : null;
        Instant markedAt = parseTimestamp(safeText(node.get("markedAt")));
        JsonNode studentNode = node.get("student");
        String studentName = studentNode != null ? safeText(studentNode.get("fullName")) : null;
        String studentNumber = studentNode != null ? safeText(studentNode.get("studentNumber")) : null;
        return new AttendanceRecordView(studentId, status, markedAt, method, confidence, studentName, studentNumber);
    }

    private AttendanceRecordView parseAttendanceRecord(String body,
                                                       String fallbackStudentId,
                                                       String fallbackStatus,
                                                       boolean manual,
                                                       Double confidence) {
        if (body == null || body.isBlank()) {
            Double resolvedConfidence = confidence != null && Double.isFinite(confidence) ? confidence : null;
            return new AttendanceRecordView(fallbackStudentId, fallbackStatus, Instant.now(),
                    manual ? "manual" : "auto", resolvedConfidence, null, null);
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            String studentId = safeText(root.get("studentId"));
            if (studentId == null || studentId.isBlank()) {
                studentId = fallbackStudentId;
            }
            String status = safeText(root.get("status"));
            if (status == null || status.isBlank()) {
                status = fallbackStatus;
            }
            String method = safeText(root.get("markingMethod"));
            if (method == null || method.isBlank()) {
                method = manual ? "manual" : "auto";
            }
            Double responseConfidence = root.hasNonNull("confidenceScore") ? root.get("confidenceScore").asDouble()
                    : (confidence != null && Double.isFinite(confidence) ? confidence : null);
            Instant markedAt = parseTimestamp(safeText(root.get("markedAt")));
            JsonNode studentNode = root.get("student");
            String studentName = studentNode != null ? safeText(studentNode.get("fullName")) : null;
            String studentNumber = studentNode != null ? safeText(studentNode.get("studentNumber")) : null;
            return new AttendanceRecordView(studentId, status, markedAt, method, responseConfidence, studentName, studentNumber);
        } catch (Exception ex) {
            log.warn("Unable to parse attendance response: {}", ex.getMessage());
            Double resolvedConfidence = confidence != null && Double.isFinite(confidence) ? confidence : null;
            return new AttendanceRecordView(fallbackStudentId, fallbackStatus, Instant.now(),
                    manual ? "manual" : "auto", resolvedConfidence, null, null);
        }
    }

    private void updateRosterFromRecord(AttendanceRecordView record) {
        if (record == null || record.studentId() == null || record.studentId().isBlank()) {
            return;
        }
        String studentId = record.studentId();
        rosterEntries.compute(studentId, (id, existing) -> {
            String name = firstNonBlank(record.studentName(), existing != null ? existing.fullName() : null,
                    studentNames.getOrDefault(id, id));
            String number = firstNonBlank(record.studentNumber(), existing != null ? existing.studentNumber() : null);
            String status = firstNonBlank(record.status(), existing != null ? existing.status() : "pending");
            String method = firstNonBlank(record.markingMethod(), existing != null ? existing.markingMethod() : null);
            Instant markedAt = record.markedAt() != null ? record.markedAt()
                    : existing != null ? existing.markedAt() : Instant.now();
            Double resultConfidence = record.confidence() != null ? record.confidence()
                    : existing != null ? existing.confidence() : null;
            return new SessionWindow.RosterEntry(id, name, number, status, markedAt, method, resultConfidence);
        });
        synchronized (rosterOrder) {
            if (!rosterOrder.contains(studentId)) {
                rosterOrder.add(studentId);
            }
        }
        if (record.status() != null
                && ("present".equalsIgnoreCase(record.status()) || "late".equalsIgnoreCase(record.status()))) {
            recordedStudents.add(studentId);
        } else {
            recordedStudents.remove(studentId);
        }
        window.updateRoster(buildRosterSnapshot());
    }

    private List<SessionWindow.RosterEntry> buildRosterSnapshot() {
        List<SessionWindow.RosterEntry> snapshot = new ArrayList<>();
        synchronized (rosterOrder) {
            for (String id : rosterOrder) {
                SessionWindow.RosterEntry entry = rosterEntries.get(id);
                if (entry != null) {
                    snapshot.add(entry);
                }
            }
        }
        return snapshot;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null) {
                String trimmed = value.trim();
                if (!trimmed.isEmpty()) {
                    return trimmed;
                }
            }
        }
        return null;
    }

    private String safeText(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String text = node.asText(null);
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Instant parseTimestamp(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value.trim()).toInstant();
        } catch (Exception ignored) {
            try {
                return Instant.parse(value.trim());
            } catch (Exception ignoredToo) {
                return null;
            }
        }
    }

    private record AttendanceRecordView(String studentId,
                                        String status,
                                        Instant markedAt,
                                        String markingMethod,
                                        Double confidence,
                                        String studentName,
                                        String studentNumber) {
    }

    private String determineAttendanceStatus(Instant markInstant) {
        if (scheduledStart == null) {
            return "present";
        }
        int threshold = Math.max(0, lateThresholdMinutes);
        if (threshold == 0) {
            return markInstant.isAfter(scheduledStart) ? "late" : "present";
        }
        Instant lateAfter = scheduledStart.plus(Duration.ofMinutes(threshold));
        return markInstant.isAfter(lateAfter) ? "late" : "present";
    }

    private Rect clamp(Rectangle rect, int width, int height) {
        int x = Math.max(0, rect.x);
        int y = Math.max(0, rect.y);
        int w = Math.min(rect.width, width - x);
        int h = Math.min(rect.height, height - y);
        return new Rect(x, y, Math.max(1, w), Math.max(1, h));
    }

    private boolean isLikelyFace(Rectangle rect, int frameWidth, int frameHeight) {
        if (rect == null || rect.width <= 0 || rect.height <= 0) {
            return false;
        }
        int minSize = (int)Math.max(96, Math.round(minFace * 0.7));
        if (rect.width < minSize || rect.height < minSize) {
            return false;
        }
        double aspect = rect.height / (double) rect.width;
        if (aspect < 0.65 || aspect > 1.55) {
            return false;
        }
        if (rect.width > frameWidth * 0.95 || rect.height > frameHeight * 0.95) {
            return false;
        }
        return true;
    }

    @Override
    public void close() {
        running.set(false);
        executor.shutdownNow();
        attendanceExecutor.shutdownNow();
        if (windowEventListener != null) {
            eventBus.unsubscribe(windowEventListener);
            windowEventListener = null;
        }
        if (window != null) {
            window.close();
        }
        if (frameSource != null) {
            frameSource.close();
            frameSource = null;
        }
        eventBus.publish(new RecognitionEvent(
                RecognitionEventType.CAMERA_STOPPED,
                Instant.now(),
                null,
                null,
                null,
                Double.NaN,
                "Camera stopped",
                true,
                false));
    }
}
