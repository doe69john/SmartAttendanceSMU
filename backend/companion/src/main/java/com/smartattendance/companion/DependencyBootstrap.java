package com.smartattendance.companion;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

final class DependencyBootstrap {

    private static final String MAVEN_BASE = "https://repo1.maven.org/maven2";
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofMinutes(2);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    void launch(String[] args) throws Exception {
        if (Boolean.getBoolean("companion.bootstrap.skip")) {
            CompanionRuntime.launch(args);
            return;
        }

        Path source = locateCodeSource();
        if (Files.isDirectory(source)) {
            // Running from IDE or exploded classes; assume dependencies are already available.
            CompanionRuntime.launch(args);
            return;
        }

        Path installDir = source.getParent();
        if (installDir == null) {
            throw new IllegalStateException("Unable to resolve installation directory from " + source);
        }
        Path libDir = installDir.resolve("lib");
        Files.createDirectories(libDir);

        ensureDependencies(libDir);
        relaunch(source, libDir, args);
    }

    private Path locateCodeSource() throws URISyntaxException {
        URI location = DependencyBootstrap.class.getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI();
        return Paths.get(location);
    }

    private void ensureDependencies(Path libDir) throws IOException, InterruptedException {
        for (Artifact artifact : commonArtifacts()) {
            downloadIfMissing(libDir, artifact);
        }
        String classifier = resolveClassifier();
        if (classifier != null) {
            for (Artifact artifact : platformArtifacts(classifier)) {
                downloadIfMissing(libDir, artifact);
            }
        } else {
            System.out.println("[companion] Unsupported platform detected; native OpenCV binaries may be missing.");
        }
    }

    private List<Artifact> commonArtifacts() {
        List<Artifact> artifacts = new ArrayList<>();
        artifacts.add(new Artifact("org.bytedeco", "opencv", "4.9.0-1.5.10", null));
        artifacts.add(new Artifact("org.bytedeco", "openblas", "0.3.26-1.5.10", null));
        artifacts.add(new Artifact("org.bytedeco", "javacpp", "1.5.10", null));
        artifacts.add(new Artifact("org.bytedeco", "ffmpeg", "7.1-1.5.10", null));
        artifacts.add(new Artifact("com.fasterxml.jackson.core", "jackson-databind", "2.18.2", null));
        artifacts.add(new Artifact("com.fasterxml.jackson.core", "jackson-core", "2.18.2", null));
        artifacts.add(new Artifact("com.fasterxml.jackson.core", "jackson-annotations", "2.18.2", null));
        artifacts.add(new Artifact("org.slf4j", "slf4j-api", "2.0.16", null));
        artifacts.add(new Artifact("org.slf4j", "slf4j-simple", "2.0.16", null));
        artifacts.add(new Artifact("org.apache.commons", "commons-lang3", "3.14.0", null));
        if (shouldUseWebcamCapture()) {
            artifacts.add(new Artifact("com.github.sarxos", "webcam-capture", "0.3.12", null));
        }
        return artifacts;
    }

    private List<Artifact> platformArtifacts(String classifier) {
        return List.of(
                new Artifact("org.bytedeco", "opencv", "4.9.0-1.5.10", classifier),
                new Artifact("org.bytedeco", "openblas", "0.3.26-1.5.10", classifier),
                new Artifact("org.bytedeco", "javacpp", "1.5.10", classifier),
                new Artifact("org.bytedeco", "ffmpeg", "7.1-1.5.10", classifier)
        );
    }

    private boolean shouldUseWebcamCapture() {
        return !isArm64Mac();
    }

    private boolean isArm64Mac() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ENGLISH);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ENGLISH);
        return os.contains("mac") && (arch.contains("arm64") || arch.contains("aarch64"));
    }

    private void downloadIfMissing(Path libDir, Artifact artifact) throws IOException, InterruptedException {
        Path target = libDir.resolve(artifact.fileName());
        if (Files.exists(target)) {
            return;
        }

        Files.createDirectories(target.getParent());
        System.out.println("[companion] Downloading " + artifact.fileName());

        Path temp = Files.createTempFile("companion-", ".jar");
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(artifact.url()))
                    .timeout(DOWNLOAD_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<Path> response = httpClient.send(request, HttpResponse.BodyHandlers.ofFile(temp));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                Files.deleteIfExists(temp);
                throw new IOException("HTTP " + response.statusCode() + " downloading " + artifact.url());
            }
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | InterruptedException ex) {
            Files.deleteIfExists(temp);
            throw ex;
        }
    }

    private void relaunch(Path jarPath, Path libDir, String[] args) throws IOException, InterruptedException {
        String javaHome = System.getProperty("java.home");
        Path javaBin = Paths.get(javaHome, "bin", isWindows() ? "java.exe" : "java");
        if (!Files.isRegularFile(javaBin)) {
            throw new IOException("Java executable not found at " + javaBin);
        }

        String classpath = buildClasspath(jarPath, libDir);
        List<String> command = new ArrayList<>();
        command.add(javaBin.toString());
        command.add("-Dcompanion.bootstrap.skip=true");
        command.add("-cp");
        command.add(classpath);
        command.add("com.smartattendance.companion.CompanionRuntime");
        command.addAll(List.of(args));

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(jarPath.getParent().toFile());
        builder.inheritIO();
        Process process = builder.start();
        int exitCode = process.waitFor();
        System.exit(exitCode);
    }

    private String buildClasspath(Path jarPath, Path libDir) throws IOException {
        String separator = isWindows() ? ";" : ":";
        StringBuilder cp = new StringBuilder(jarPath.toAbsolutePath().toString());
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(libDir, "*.jar")) {
            for (Path jar : stream) {
                cp.append(separator).append(jar.toAbsolutePath());
            }
        }
        return cp.toString();
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ENGLISH).contains("win");
    }

    private String resolveClassifier() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ENGLISH);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ENGLISH);
        boolean arm = arch.contains("aarch64") || arch.contains("arm64");

        if (os.contains("mac")) {
            return arm ? "macosx-arm64" : "macosx-x86_64";
        }
        if (os.contains("win")) {
            return arch.contains("64") ? "windows-x86_64" : "windows-x86";
        }
        if (os.contains("linux")) {
            if (arm) {
                return "linux-arm64";
            }
            if (arch.contains("ppc64le")) {
                return "linux-ppc64le";
            }
            return arch.contains("64") ? "linux-x86_64" : null;
        }
        return null;
    }

    private record Artifact(String groupId, String artifactId, String version, String classifier) {
        Artifact {
            Objects.requireNonNull(groupId, "groupId");
            Objects.requireNonNull(artifactId, "artifactId");
            Objects.requireNonNull(version, "version");
        }

        String fileName() {
            return artifactId + "-" + version + (classifier != null ? "-" + classifier : "") + ".jar";
        }

        String url() {
            String path = groupId.replace('.', '/') + "/" + artifactId + "/" + version + "/" + fileName();
            return MAVEN_BASE + "/" + path;
        }
    }
}
