package com.smartattendance.companion.recognition;

import java.awt.Color;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import com.smartattendance.vision.tracking.FaceTrack;

/**
 * Wraps a {@link FaceTrack} with recognition-specific metadata required for
 * rendering overlays and avoiding duplicate attendance submissions.
 */
public final class TrackedFace {
    private final FaceTrack track;
    private volatile FaceTrackState state = FaceTrackState.DETECTED;
    private volatile String studentId;
    private volatile String studentName;
    private volatile double lastConfidence = Double.NaN;
    private volatile Instant lastAttempt = Instant.EPOCH;
    private final AtomicInteger attempts = new AtomicInteger();
    private final AtomicInteger manualPromptAttempts = new AtomicInteger();
    private volatile boolean manualPrompted;
    private volatile Color overlayColor = Color.YELLOW;

    public TrackedFace(FaceTrack track) {
        this.track = track;
    }

    public FaceTrack track() {
        return track;
    }

    public FaceTrackState state() {
        return state;
    }

    public void setState(FaceTrackState state) {
        if (state != null) {
            this.state = state;
        }
    }

    public String studentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public String studentName() {
        return studentName;
    }

    public void setStudentName(String studentName) {
        this.studentName = studentName;
    }

    public double lastConfidence() {
        return lastConfidence;
    }

    public void setLastConfidence(double lastConfidence) {
        this.lastConfidence = lastConfidence;
    }

    public Instant lastAttempt() {
        return lastAttempt;
    }

    public void markAttempt(Instant instant) {
        this.lastAttempt = instant != null ? instant : Instant.now();
        attempts.incrementAndGet();
    }

    public int attempts() {
        return attempts.get();
    }

    public int manualPromptAttempts() {
        return manualPromptAttempts.get();
    }

    public int incrementManualPromptAttempts() {
        return manualPromptAttempts.incrementAndGet();
    }

    public boolean manualPrompted() {
        return manualPrompted;
    }

    public void setManualPrompted(boolean manualPrompted) {
        this.manualPrompted = manualPrompted;
    }

    public Color overlayColor() {
        return overlayColor;
    }

    public void setOverlayColor(Color overlayColor) {
        if (overlayColor != null) {
            this.overlayColor = overlayColor;
        }
    }
}
