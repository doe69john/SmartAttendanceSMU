package com.smartattendance.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * Loads the runtime {@code config.properties} file that mirrors the desktop application's
 * behaviour. The configuration is reloaded whenever the file changes and exposed through
 * strongly typed view models for downstream services.
 */
@Component
public class AttendanceProperties {
    private static final Logger log = LoggerFactory.getLogger(AttendanceProperties.class);

    private static final Path CONFIG_FILE = detectConfigFile();

    private final AtomicReference<ConfigSnapshot> snapshot = new AtomicReference<>();
    private final CopyOnWriteArrayList<Consumer<ConfigSnapshot>> listeners = new CopyOnWriteArrayList<>();

    private WatchService watchService;
    private Thread watcherThread;

    public AttendanceProperties() {
        ConfigSnapshot initial = loadSnapshot();
        snapshot.set(initial);
        ensureDirectories(initial.directories());
    }

    @PostConstruct
    void init() {
        reloadFromDisk();
        startWatcher();
    }

    @PreDestroy
    void shutdown() {
        Thread thread = watcherThread;
        watcherThread = null;
        if (thread != null) {
            thread.interrupt();
        }
        WatchService ws = watchService;
        watchService = null;
        if (ws != null) {
            try {
                ws.close();
            } catch (IOException ignored) {
            }
        }
    }

    /** Register a listener that receives the current configuration and future reloads. */
    public void onChange(Consumer<ConfigSnapshot> listener) {
        if (listener == null) {
            return;
        }
        listeners.add(listener);
        ConfigSnapshot snap = snapshot.get();
        if (snap != null) {
            try {
                listener.accept(snap);
            } catch (Exception ex) {
                log.warn("AttendanceProperties listener threw during initial notification: {}", ex.toString());
            }
        }
    }

    /** Returns the current config snapshot. */
    public ConfigSnapshot snapshot() {
        return snapshot.get();
    }

    public Path configFile() {
        return snapshot().configFile();
    }

    public Directories directories() {
        return snapshot().directories();
    }

    public Camera camera() {
        return snapshot().camera();
    }

    public Capture capture() {
        return snapshot().capture();
    }

    public Recognition recognition() {
        return snapshot().recognition();
    }

    public Live live() {
        return snapshot().live();
    }

    public Detection detection() {
        return snapshot().detection();
    }

    public Preprocessing preprocessing() {
        return snapshot().preprocessing();
    }

    /** Forces a reload from disk immediately. */
    public synchronized void reloadFromDisk() {
        ConfigSnapshot updated = loadSnapshot();
        snapshot.set(updated);
        ensureDirectories(updated.directories());
        notifyListeners(updated);
    }

    private void notifyListeners(ConfigSnapshot snap) {
        for (Consumer<ConfigSnapshot> listener : listeners) {
            try {
                listener.accept(snap);
            } catch (Exception ex) {
                log.warn("AttendanceProperties listener threw: {}", ex.toString());
            }
        }
    }

    private void startWatcher() {
        if (watcherThread != null) {
            return;
        }
        Path file = CONFIG_FILE;
        Path dir = file.getParent();
        if (dir == null) {
            log.debug("Config file has no parent directory; watcher disabled (file={})", file);
            return;
        }
        try {
            Files.createDirectories(dir);
        } catch (IOException ex) {
            log.warn("Failed to create config directory {}: {}", dir, ex.toString());
        }
        try {
            watchService = FileSystems.getDefault().newWatchService();
            dir.register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY);
        } catch (IOException ex) {
            log.warn("Unable to watch config directory {}: {}", dir, ex.toString());
            return;
        }
        watcherThread = new Thread(() -> watchLoop(file.getFileName()), "attendance-config-watcher");
        watcherThread.setDaemon(true);
        watcherThread.start();
    }

    private void watchLoop(Path fileName) {
        while (!Thread.currentThread().isInterrupted()) {
            WatchKey key;
            try {
                key = watchService.take();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            } catch (ClosedWatchServiceException ex) {
                break;
            }
            boolean reload = false;
            for (WatchEvent<?> event : key.pollEvents()) {
                Object ctx = event.context();
                if (ctx instanceof Path changed && changed.getFileName().equals(fileName)) {
                    reload = true;
                }
            }
            boolean valid = key.reset();
            if (!valid) {
                break;
            }
            if (reload) {
                try {
                    Thread.sleep(200L);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    break;
                }
                try {
                    reloadFromDisk();
                } catch (Exception ex) {
                    log.warn("Failed to reload runtime config: {}", ex.toString());
                }
            }
        }
    }

    private static Path detectConfigFile() {
        Path repoRelative = Paths.get("backend", "runtime", "config.properties");
        if (Files.exists(repoRelative)) {
            return repoRelative.toAbsolutePath().normalize();
        }
        Path moduleRelative = Paths.get("runtime", "config.properties");
        if (Files.exists(moduleRelative)) {
            return moduleRelative.toAbsolutePath().normalize();
        }
        return repoRelative.toAbsolutePath().normalize();
    }

    private ConfigSnapshot loadSnapshot() {
        Properties props = new Properties();
        Path config = CONFIG_FILE;
        if (Files.exists(config)) {
            try (InputStream in = Files.newInputStream(config)) {
                props.load(in);
            } catch (IOException ex) {
                log.warn("Unable to read runtime config {}; using defaults: {}", config, ex.toString());
            }
        } else {
            log.info("Runtime config {} does not exist yet; defaults will be used", config);
        }
        Path baseDir = config.getParent();
        if (baseDir == null) {
            baseDir = Paths.get("");
        }
        Path sanitizedBase = sanitize(baseDir.toAbsolutePath().normalize());

        Path defaultDataDir = sanitizedBase.resolve("data");
        Path dataDir = resolvePath(props.getProperty("data.dir"), sanitizedBase, defaultDataDir);
        Path facesDir = resolvePath(props.getProperty("faces.dir"), sanitizedBase, dataDir.resolve("faces"));
        Path modelDir = resolvePath(props.getProperty("model.dir"), sanitizedBase, dataDir.resolve("model"));

        Directories directories = new Directories(dataDir, facesDir, modelDir);

        Camera camera = new Camera(
                getInt(props, "camera.index", 0),
                getDouble(props, "camera.fps", 60.0));

        Capture capture = new Capture(
                getDouble(props, "capture.blur.variance_threshold", 70.0),
                getDouble(props, "capture.post_blur.variance_threshold", 100.0));

        Recognition recognition = new Recognition(
                new Lbph(
                        getInt(props, "recognition.lbph.radius", 3),
                        getInt(props, "recognition.lbph.neighbors", 8),
                        getInt(props, "recognition.lbph.grid_x", 12),
                        getInt(props, "recognition.lbph.grid_y", 12)));

        Live live = new Live(
                new LiveRecognition(
                        getInt(props, "live.recognize.min_frames", 8),
                        getDouble(props, "live.recognize.motion_threshold", 80.0),
                        getInt(props, "live.recognize.min_face", 160),
                        getInt(props, "live.recognize.interval_ms", 1500)),
                getDouble(props, "live.blur.variance_threshold", 60.0));

        Detection detection = new Detection(
                getDouble(props, "detect.scale", 1.0),
                getInt(props, "detect.min_face", 160),
                getDouble(props, "detect.cascade.scale_factor", 1.2),
                getInt(props, "detect.cascade.min_neighbors", 8));

        Preprocessing preprocessing = new Preprocessing(
                getInt(props, "preproc.width", 256),
                getInt(props, "preproc.height", 256),
                getDouble(props, "detect.scale", 1.0));

        return new ConfigSnapshot(config, directories, camera, capture,
                recognition, live, detection, preprocessing);
    }

    private static Path resolvePath(String raw, Path baseDir, Path defaultPath) {
        Path resolved;
        String cleaned = cleanValue(raw);
        if (cleaned == null || cleaned.isBlank()) {
            resolved = defaultPath;
        } else {
            String normalized = cleaned.replace("\\", "/");
            Path candidate = Paths.get(normalized);
            if (!candidate.isAbsolute()) {
                resolved = baseDir.resolve(normalized);
            } else {
                resolved = candidate;
            }
        }
        return sanitize(resolved.toAbsolutePath().normalize());
    }

    private static Path sanitize(Path path) {
        if (path == null) {
            return null;
        }
        return path.normalize();
    }

    private static void ensureDirectories(Directories directories) {
        if (directories != null) {
            createIfMissing(directories.dataDir());
            createIfMissing(directories.facesDir());
            createIfMissing(directories.modelDir());
        }
    }

    private static void createIfMissing(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.createDirectories(path);
        } catch (IOException ex) {
            log.warn("Failed to create directory {}: {}", path, ex.getMessage());
        }
    }

    private static int getInt(Properties props, String key, int defaultValue) {
        String value = cleanValue(props.getProperty(key));
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            log.warn("Invalid integer for {} (value='{}'); using {}", key, value, defaultValue);
            return defaultValue;
        }
    }

    private static double getDouble(Properties props, String key, double defaultValue) {
        String value = cleanValue(props.getProperty(key));
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException ex) {
            log.warn("Invalid decimal for {} (value='{}'); using {}", key, value, defaultValue);
            return defaultValue;
        }
    }

    private static boolean getBoolean(Properties props, String key, boolean defaultValue) {
        String value = cleanValue(props.getProperty(key));
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value.trim());
    }

    private static String getString(Properties props, String key, String defaultValue) {
        String value = cleanValue(props.getProperty(key));
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim();
    }

    private static String cleanValue(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        int len = trimmed.length();
        boolean escaped = false;
        for (int i = 0; i < len; i++) {
            char c = trimmed.charAt(i);
            if (c == '\\' && !escaped) {
                escaped = true;
                continue;
            }
            if ((c == '#' || c == ';') && !escaped) {
                return trimmed.substring(0, i).trim();
            }
            escaped = false;
        }
        return trimmed;
    }

    public record ConfigSnapshot(
            Path configFile,
            Directories directories,
            Camera camera,
            Capture capture,
            Recognition recognition,
            Live live,
            Detection detection,
            Preprocessing preprocessing) {
    }

    public record Directories(Path dataDir, Path facesDir, Path modelDir) {
    }

    public record Camera(int index, double fps) {
    }

    public record Capture(double blurVarianceThreshold, double postCaptureBlurVarianceThreshold) {
    }

    public record Recognition(Lbph lbph) {
    }

    public record Lbph(int radius, int neighbors, int gridX, int gridY) {
    }

    public record Live(LiveRecognition recognition, double blurVarianceThreshold) {
    }

    public record LiveRecognition(int minFrames, double motionThreshold, int minFace, int intervalMs) {
    }

    public record Detection(double downscale, int minFace, double cascadeScaleFactor, int cascadeMinNeighbors) {
    }

    public record Preprocessing(int width, int height, double detectionScale) {
    }
}
