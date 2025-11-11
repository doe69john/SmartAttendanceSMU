package com.smartattendance.supabase.service.companion;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CompanionInstallerBuilder {

    private static final Logger logger = LoggerFactory.getLogger(CompanionInstallerBuilder.class);

    private static final String COMPANION_MODULE = "companion";
    private static final String VERSION_RESOURCE = "src/main/resources/companion-version.properties";
    private static final String COMPANION_JAR_NAME = "smartattendance-companion.jar";

    private final Path backendDirectory;
    private final boolean windows;
    private final Path projectRoot;
    private final Path companionModule;

    public CompanionInstallerBuilder(Path backendDirectory) {
        this.backendDirectory = Objects.requireNonNull(backendDirectory, "backendDirectory");
        this.windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        this.projectRoot = resolveProjectRoot();
        this.companionModule = resolveCompanionModule(projectRoot);
    }

    public BuildArtifacts buildInstallers() {
        if (companionModule == null) {
            throw new IllegalStateException("Companion module is not available for installer build");
        }

        String version = readCompanionVersion(companionModule);
        runMavenBuild(projectRoot);

        Path jarPath = companionModule.resolve(Path.of("target", COMPANION_JAR_NAME));
        if (!Files.exists(jarPath)) {
            throw new IllegalStateException("Companion jar not produced: " + jarPath.toAbsolutePath());
        }

        try {
            Path staging = Files.createTempDirectory("companion-build");
            Path macArchive = createMacPackage(staging, jarPath, version);
            Path windowsArchive = createWindowsPackage(staging, jarPath, version);
            BuildArtifacts artifacts = new BuildArtifacts(
                    version,
                    macArchive,
                    windowsArchive,
                    fileSize(macArchive),
                    fileSize(windowsArchive),
                    checksum(macArchive),
                    checksum(windowsArchive),
                    Instant.now());
            logger.info("Prepared companion installers (version {}): mac={} windows={}",
                    version,
                    macArchive.toAbsolutePath(),
                    windowsArchive.toAbsolutePath());
            return artifacts;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to prepare companion installers", ex);
        }
    }

    private Path resolveProjectRoot() {
        if (isProjectRoot(backendDirectory)) {
            return backendDirectory;
        }
        Path parent = backendDirectory.getParent();
        if (parent != null && isProjectRoot(parent)) {
            return parent;
        }
        logger.warn("Falling back to backend directory {} as project root; Maven wrapper may be missing.", backendDirectory);
        return backendDirectory;
    }

    private boolean isProjectRoot(Path candidate) {
        if (candidate == null) {
            return false;
        }
        boolean hasPom = Files.exists(candidate.resolve("pom.xml"));
        boolean hasWrapper = Files.exists(candidate.resolve("mvnw")) || Files.exists(candidate.resolve("mvnw.cmd"));
        return hasPom && hasWrapper;
    }

    private Path resolveCompanionModule(Path projectRoot) {
        Path direct = projectRoot.resolve(COMPANION_MODULE);
        if (Files.isDirectory(direct)) {
            logger.debug("Resolved companion module at {}", direct.toAbsolutePath());
            return direct;
        }
        Path nested = backendDirectory.resolve(COMPANION_MODULE);
        if (Files.isDirectory(nested)) {
            logger.debug("Resolved companion module at {}", nested.toAbsolutePath());
            return nested;
        }
        logger.info("Companion module not found. Checked {} and {}", direct.toAbsolutePath(), nested.toAbsolutePath());
        return null;
    }

    private Path findMavenWrapper(Path projectRoot) {
        Path wrapper = selectWrapper(projectRoot);
        if (wrapper != null) {
            return wrapper;
        }
        if (!projectRoot.equals(backendDirectory)) {
            wrapper = selectWrapper(backendDirectory);
            if (wrapper != null) {
                return wrapper;
            }
        }
        Path parent = backendDirectory.getParent();
        if (parent != null) {
            wrapper = selectWrapper(parent);
            if (wrapper != null) {
                return wrapper;
            }
        }
        throw new IllegalStateException("Maven wrapper not found near " + backendDirectory.toAbsolutePath());
    }

    private Path selectWrapper(Path directory) {
        if (directory == null) {
            return null;
        }
        Path preferred = windows ? directory.resolve("mvnw.cmd") : directory.resolve("mvnw");
        if (Files.exists(preferred)) {
            return preferred;
        }
        Path alternate = windows ? directory.resolve("mvnw") : directory.resolve("mvnw.cmd");
        if (Files.exists(alternate)) {
            return alternate;
        }
        return null;
    }

    private String readCompanionVersion(Path companionModule) {
        Path versionFile = companionModule.resolve(VERSION_RESOURCE);
        if (!Files.exists(versionFile)) {
            logger.warn("Version descriptor '{}' missing. Falling back to timestamp version.", versionFile);
            return "dev-" + UUID.randomUUID();
        }
        Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(versionFile)) {
            properties.load(inputStream);
            String version = properties.getProperty("version");
            if (version == null || version.isBlank()) {
                return "dev-" + UUID.randomUUID();
            }
            return version.trim();
        } catch (IOException ex) {
            logger.warn("Unable to read companion version from {}: {}", versionFile, ex.getMessage());
            return "dev-" + UUID.randomUUID();
        }
    }

    private void runMavenBuild(Path projectRoot) {
        Path wrapper = findMavenWrapper(projectRoot);
        if (!windows) {
            wrapper.toFile().setExecutable(true);
        }
        ProcessBuilder builder;
        if (windows) {
            builder = new ProcessBuilder("cmd.exe", "/c", wrapper.getFileName().toString(),
                    "-pl", COMPANION_MODULE, "-am", "package", "-DskipTests");
        } else {
            builder = new ProcessBuilder(wrapper.toAbsolutePath().toString(),
                    "-pl", COMPANION_MODULE, "-am", "package", "-DskipTests");
        }
        builder.directory(wrapper.getParent().toFile());
        builder.redirectErrorStream(true);
        try {
            Process process = builder.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logger.debug("[mvn] {}", line);
                }
            }
            boolean exited = process.waitFor(5, TimeUnit.MINUTES);
            if (!exited) {
                process.destroyForcibly();
                throw new IllegalStateException("Maven build for companion module timed out");
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException("Maven build failed with exit code " + process.exitValue());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Failed to build companion module", ex);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to build companion module", ex);
        }
    }

    public boolean isCompanionModulePresent() {
        return companionModule != null;
    }

    public Path getCompanionModule() {
        return companionModule;
    }

    private Path createMacPackage(Path stagingRoot, Path jarPath, String version) throws IOException {
        Path macRoot = stagingRoot.resolve("mac").resolve("SmartAttendanceCompanion");
        Files.createDirectories(macRoot);
        Files.createDirectories(macRoot.resolve("runtime"));
        Files.createDirectories(macRoot.resolve("lib"));
        copyJar(jarPath, macRoot.resolve("SmartAttendanceCompanion.jar"));
        copyRuntimeConfig(macRoot);
        writeFile(macRoot.resolve("README.txt"), macReadme(version));
        Path startScript = macRoot.resolve("start.sh");
        writeFile(startScript, macStartScript());
        setExecutable(startScript);
        Path zipTarget = stagingRoot.resolve("smartattendance-companion-mac-" + version + ".zip");
        zipDirectory(macRoot.getParent(), zipTarget);
        return zipTarget;
    }

    private Path createWindowsPackage(Path stagingRoot, Path jarPath, String version) throws IOException {
        Path winRoot = stagingRoot.resolve("windows").resolve("SmartAttendanceCompanion");
        Files.createDirectories(winRoot);
        Files.createDirectories(winRoot.resolve("runtime"));
        Files.createDirectories(winRoot.resolve("lib"));
        copyJar(jarPath, winRoot.resolve("SmartAttendanceCompanion.jar"));
        copyRuntimeConfig(winRoot);
        writeFile(winRoot.resolve("README.txt"), windowsReadme(version));
        Path startBat = winRoot.resolve("start.bat");
        writeFile(startBat, windowsStartBat());
        Path startPs1 = winRoot.resolve("start.ps1");
        writeFile(startPs1, windowsStartPs1());
        Path zipTarget = stagingRoot.resolve("smartattendance-companion-windows-" + version + ".zip");
        zipDirectory(winRoot.getParent(), zipTarget);
        return zipTarget;
    }

    private void copyJar(Path sourceJar, Path destinationJar) throws IOException {
        Files.createDirectories(destinationJar.getParent());
        Files.copy(sourceJar, destinationJar, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private void copyRuntimeConfig(Path bundleRoot) throws IOException {
        Path sourceConfig = backendDirectory.resolve("runtime").resolve("config.properties");
        if (!Files.exists(sourceConfig)) {
            return;
        }
        Path targetDir = bundleRoot.resolve("backend").resolve("runtime");
        Files.createDirectories(targetDir);
        Files.copy(sourceConfig, targetDir.resolve("config.properties"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
    private void writeFile(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    private void setExecutable(Path path) {
        try {
            boolean success = path.toFile().setExecutable(true, true);
            if (!success) {
                logger.debug("Unable to mark {} as executable", path);
            }
        } catch (SecurityException ex) {
            logger.debug("Unable to adjust executable bit for {}: {}", path, ex.getMessage());
        }
    }

    private void zipDirectory(Path sourceDirectory, Path destinationZip) throws IOException {
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(destinationZip))) {
            zipOutputStream.setLevel(Deflater.BEST_COMPRESSION);
            Files.walkFileTree(sourceDirectory, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Path relative = sourceDirectory.relativize(file);
                    ZipEntry entry = new ZipEntry(relative.toString().replace('\\', '/'));
                    zipOutputStream.putNextEntry(entry);
                    Files.copy(file, zipOutputStream);
                    zipOutputStream.closeEntry();
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    Path relative = sourceDirectory.relativize(dir);
                    if (!relative.toString().isEmpty()) {
                        ZipEntry entry = new ZipEntry(relative.toString().replace('\\', '/') + "/");
                        zipOutputStream.putNextEntry(entry);
                        zipOutputStream.closeEntry();
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    private long fileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to determine size of " + path, ex);
        }
    }

    private String checksum(Path path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] data = Files.readAllBytes(path);
            byte[] hash = digest.digest(data);
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (IOException | NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Failed to compute checksum for " + path, ex);
        }
    }

    private String macReadme(String version) {
        return "SmartAttendance Companion (macOS)\n" +
                "Version: " + version + "\n\n" +
                "1. Extract the SmartAttendanceCompanion folder.\n" +
                "2. Double-click start.sh or run './start.sh' from Terminal.\n" +
                "3. On first launch the script downloads a lightweight JRE (if needed) and the OpenCV libraries required for recognition.\n" +
                "4. Subsequent launches reuse the cached runtime and libraries stored inside the folder.\n" +
                "5. Keep the app running while managing live sessions from the SmartAttendance dashboard.\n";
    }

    private String macStartScript() {
        return "#!/usr/bin/env bash\n" +
                "set -euo pipefail\n" +
                "DIR=\"$(cd \"$(dirname \"$0\")\" && pwd)\"\n" +
                "RUNTIME_DIR=\"$DIR/runtime\"\n" +
                "LIB_DIR=\"$DIR/lib\"\n" +
                "JAVA_CMD=\"$RUNTIME_DIR/bin/java\"\n" +
                "download_jar() {\n" +
                "  local url=\"$1\"\n" +
                "  local target=\"$2\"\n" +
                "  if [ ! -f \"$target\" ]; then\n" +
                "    echo \"Downloading $(basename \\\"$target\\\")...\"\n" +
                "    curl -L \"$url\" -o \"$target\"\n" +
                "  fi\n" +
                "}\n" +
                "ensure_dependencies() {\n" +
                "  mkdir -p \"$LIB_DIR\"\n" +
                "  local base=\"https://repo1.maven.org/maven2\"\n" +
                "  download_jar \"$base/org/bytedeco/opencv/4.9.0-1.5.10/opencv-4.9.0-1.5.10.jar\" \"$LIB_DIR/opencv-4.9.0-1.5.10.jar\"\n" +
                "  download_jar \"$base/org/bytedeco/javacpp/1.5.10/javacpp-1.5.10.jar\" \"$LIB_DIR/javacpp-1.5.10.jar\"\n" +
                "  download_jar \"$base/com/fasterxml/jackson/core/jackson-databind/2.18.2/jackson-databind-2.18.2.jar\" \"$LIB_DIR/jackson-databind-2.18.2.jar\"\n" +
                "  download_jar \"$base/com/fasterxml/jackson/core/jackson-core/2.18.2/jackson-core-2.18.2.jar\" \"$LIB_DIR/jackson-core-2.18.2.jar\"\n" +
                "  download_jar \"$base/com/fasterxml/jackson/core/jackson-annotations/2.18.2/jackson-annotations-2.18.2.jar\" \"$LIB_DIR/jackson-annotations-2.18.2.jar\"\n" +
                "  download_jar \"$base/org/slf4j/slf4j-api/2.0.16/slf4j-api-2.0.16.jar\" \"$LIB_DIR/slf4j-api-2.0.16.jar\"\n" +
                "  download_jar \"$base/org/slf4j/slf4j-simple/2.0.16/slf4j-simple-2.0.16.jar\" \"$LIB_DIR/slf4j-simple-2.0.16.jar\"\n" +
                "  download_jar \"$base/org/apache/commons/commons-lang3/3.14.0/commons-lang3-3.14.0.jar\" \"$LIB_DIR/commons-lang3-3.14.0.jar\"\n" +
                "  download_jar \"$base/com/github/sarxos/webcam-capture/0.3.12/webcam-capture-0.3.12.jar\" \"$LIB_DIR/webcam-capture-0.3.12.jar\"\n" +
                "  download_jar \"$base/com/nativelibs4java/bridj/0.7.0/bridj-0.7.0.jar\" \"$LIB_DIR/bridj-0.7.0.jar\"\n" +
                "  local arch=\"$(uname -m)\"\n" +
                "  local classifier=\"macosx-x86_64\"\n" +
                "  if [ \"$arch\" = \"arm64\" ]; then\n" +
                "    classifier=\"macosx-arm64\"\n" +
                "  fi\n" +
                "  download_jar \"$base/org/bytedeco/opencv/4.9.0-1.5.10/opencv-4.9.0-1.5.10-${classifier}.jar\" \"$LIB_DIR/opencv-4.9.0-1.5.10-${classifier}.jar\"\n" +
                "  download_jar \"$base/org/bytedeco/openblas/0.3.26-1.5.10/openblas-0.3.26-1.5.10-${classifier}.jar\" \"$LIB_DIR/openblas-0.3.26-1.5.10-${classifier}.jar\"\n" +
                "  download_jar \"$base/org/bytedeco/openblas/0.3.26-1.5.10/openblas-0.3.26-1.5.10.jar\" \"$LIB_DIR/openblas-0.3.26-1.5.10.jar\"\n" +
                "  download_jar \"$base/org/bytedeco/javacpp/1.5.10/javacpp-1.5.10-${classifier}.jar\" \"$LIB_DIR/javacpp-1.5.10-${classifier}.jar\"\n" +
                "}\n" +
                "if [ ! -x \"$JAVA_CMD\" ]; then\n" +
                "  if command -v java >/dev/null 2>&1; then\n" +
                "    JAVA_CMD=\"$(command -v java)\"\n" +
                "  else\n" +
                "    echo 'Downloading embedded Java runtime (first launch only)...'\n" +
                "    ARCH=\"$(uname -m)\"\n" +
                "    if [ \"$ARCH\" = \"arm64\" ]; then\n" +
                "      JRE_URL='https://cdn.azul.com/zulu/bin/zulu17.52.17-ca-jre17.0.12-macosx_aarch64.tar.gz'\n" +
                "    else\n" +
                "      JRE_URL='https://cdn.azul.com/zulu/bin/zulu17.52.17-ca-jre17.0.12-macosx_x64.tar.gz'\n" +
                "    fi\n" +
                "    TMP_DIR=\"$(mktemp -d)\"\n" +
                "    curl -L \"$JRE_URL\" -o \"$TMP_DIR/jre.tgz\"\n" +
                "    mkdir -p \"$RUNTIME_DIR\"\n" +
                "    tar -xzf \"$TMP_DIR/jre.tgz\" -C \"$TMP_DIR\"\n" +
                "    FOUND=\"$(find \"$TMP_DIR\" -maxdepth 2 -type d -name 'zulu17*' | head -n 1)\"\n" +
                "    if [ -n \"$FOUND\" ]; then\n" +
                "      cp -R \"$FOUND/Contents/Home/\"* \"$RUNTIME_DIR\" 2>/dev/null || cp -R \"$FOUND\"/* \"$RUNTIME_DIR\"\n" +
                "    fi\n" +
                "    rm -rf \"$TMP_DIR\"\n" +
                "    JAVA_CMD=\"$RUNTIME_DIR/bin/java\"\n" +
                "  fi\n" +
                "fi\n" +
                "ensure_dependencies\n" +
                "CLASSPATH=\"$DIR/SmartAttendanceCompanion.jar\"\n" +
                "for jar in \"$LIB_DIR\"/*.jar; do\n" +
                "  CLASSPATH=\"$CLASSPATH:$jar\"\n" +
                "done\n" +
                "exec \"$JAVA_CMD\" -cp \"$CLASSPATH\" com.smartattendance.companion.CompanionApplication \"$@\"\n";
    }

    private String windowsReadme(String version) {
        return "SmartAttendance Companion (Windows)\n" +
                "Version: " + version + "\n\n" +
                "1. Extract the SmartAttendanceCompanion folder.\n" +
                "2. Double-click start.bat (or run start.ps1) to launch.\n" +
                "3. On first launch the script downloads a lightweight JRE (if needed) and the OpenCV libraries required for recognition.\n" +
                "4. Subsequent launches reuse the cached runtime and libraries stored inside the folder.\n" +
                "5. Keep the app running while managing live sessions from the SmartAttendance dashboard.\n";
    }

    private String windowsStartBat() {
        return "@echo off\r\n" +
                "setlocal\r\n" +
                "set SCRIPT_DIR=%~dp0\r\n" +
                "powershell -ExecutionPolicy Bypass -File \"%SCRIPT_DIR%start.ps1\" %*\r\n" +
                "set EXIT_CODE=%ERRORLEVEL%\r\n" +
                "if %EXIT_CODE% neq 0 (\r\n" +
                "  echo Companion exited with error %EXIT_CODE%.\r\n" +
                "  pause\r\n" +
                ")\r\n" +
                "exit /b %EXIT_CODE%\r\n";
    }

    private String windowsStartPs1() {
        return "Param([String[]]$Args)\n" +
                "$ErrorActionPreference = 'Stop'\n" +
                "function Download-Jar {\n" +
                "  param([string]$Url, [string]$Target)\n" +
                "  if (-not (Test-Path $Target)) {\n" +
                "    Write-Host \"Downloading $(Split-Path $Target -Leaf)...\"\n" +
                "    Invoke-WebRequest -Uri $Url -OutFile $Target\n" +
                "  }\n" +
                "}\n" +
                "function Ensure-Dependencies {\n" +
                "  param([string]$LibDir)\n" +
                "  if (-not (Test-Path $LibDir)) {\n" +
                "    New-Item -ItemType Directory -Path $LibDir -Force | Out-Null\n" +
                "  }\n" +
                "  $base = 'https://repo1.maven.org/maven2'\n" +
                "  Download-Jar \"$base/org/bytedeco/opencv/4.9.0-1.5.10/opencv-4.9.0-1.5.10.jar\" (Join-Path $LibDir 'opencv-4.9.0-1.5.10.jar')\n" +
                "  Download-Jar \"$base/org/bytedeco/javacpp/1.5.10/javacpp-1.5.10.jar\" (Join-Path $LibDir 'javacpp-1.5.10.jar')\n" +
                "  Download-Jar \"$base/com/fasterxml/jackson/core/jackson-databind/2.18.2/jackson-databind-2.18.2.jar\" (Join-Path $LibDir 'jackson-databind-2.18.2.jar')\n" +
                "  Download-Jar \"$base/com/fasterxml/jackson/core/jackson-core/2.18.2/jackson-core-2.18.2.jar\" (Join-Path $LibDir 'jackson-core-2.18.2.jar')\n" +
                "  Download-Jar \"$base/com/fasterxml/jackson/core/jackson-annotations/2.18.2/jackson-annotations-2.18.2.jar\" (Join-Path $LibDir 'jackson-annotations-2.18.2.jar')\n" +
                "  Download-Jar \"$base/org/slf4j/slf4j-api/2.0.16/slf4j-api-2.0.16.jar\" (Join-Path $LibDir 'slf4j-api-2.0.16.jar')\n" +
                "  Download-Jar \"$base/org/slf4j/slf4j-simple/2.0.16/slf4j-simple-2.0.16.jar\" (Join-Path $LibDir 'slf4j-simple-2.0.16.jar')\n" +
                "  Download-Jar \"$base/org/apache/commons/commons-lang3/3.14.0/commons-lang3-3.14.0.jar\" (Join-Path $LibDir 'commons-lang3-3.14.0.jar')\n" +
                "  Download-Jar \"$base/com/github/sarxos/webcam-capture/0.3.12/webcam-capture-0.3.12.jar\" (Join-Path $LibDir 'webcam-capture-0.3.12.jar')\n" +
                "  Download-Jar \"$base/com/nativelibs4java/bridj/0.7.0/bridj-0.7.0.jar\" (Join-Path $LibDir 'bridj-0.7.0.jar')\n" +
                "  $classifier = 'windows-x86_64'\n" +
                "  Download-Jar \"$base/org/bytedeco/opencv/4.9.0-1.5.10/opencv-4.9.0-1.5.10-$classifier.jar\" (Join-Path $LibDir \"opencv-4.9.0-1.5.10-$classifier.jar\")\n" +
                "  Download-Jar \"$base/org/bytedeco/openblas/0.3.26-1.5.10/openblas-0.3.26-1.5.10-$classifier.jar\" (Join-Path $LibDir \"openblas-0.3.26-1.5.10-$classifier.jar\")\n" +
                "  Download-Jar \"$base/org/bytedeco/openblas/0.3.26-1.5.10/openblas-0.3.26-1.5.10.jar\" (Join-Path $LibDir 'openblas-0.3.26-1.5.10.jar')\n" +
                "  Download-Jar \"$base/org/bytedeco/javacpp/1.5.10/javacpp-1.5.10-$classifier.jar\" (Join-Path $LibDir \"javacpp-1.5.10-$classifier.jar\")\n" +
                "}\n" +
                "try {\n" +
                "  $scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition\n" +
                "  $runtimeDir = Join-Path $scriptDir 'runtime'\n" +
                "  $libDir = Join-Path $scriptDir 'lib'\n" +
                "  $javaExe = Join-Path $runtimeDir 'bin\\java.exe'\n" +
                "  if (-Not (Test-Path $javaExe)) {\n" +
                "    if (Get-Command java.exe -ErrorAction SilentlyContinue) {\n" +
                "      $javaExe = (Get-Command java.exe).Source\n" +
                "    } else {\n" +
                "      Write-Host 'Downloading embedded Java runtime (first launch only)...'\n" +
                "      $temp = New-Item -ItemType Directory -Path ([System.IO.Path]::GetTempPath()) -Name ('companion-' + [System.Guid]::NewGuid())\n" +
                "      $zipPath = Join-Path $temp 'jre.zip'\n" +
                "      $download = 'https://cdn.azul.com/zulu/bin/zulu17.52.17-ca-jre17.0.12-win_x64.zip'\n" +
                "      Invoke-WebRequest -Uri $download -OutFile $zipPath\n" +
                "      Expand-Archive -LiteralPath $zipPath -DestinationPath $temp\n" +
                "      $folder = Get-ChildItem -Path $temp -Directory -Filter 'zulu17*' | Select-Object -First 1\n" +
                "      if ($null -ne $folder) {\n" +
                "        New-Item -ItemType Directory -Path $runtimeDir -Force | Out-Null\n" +
                "        Copy-Item -Path (Join-Path $folder.FullName '*') -Destination $runtimeDir -Recurse -Force\n" +
                "        $javaExe = Join-Path $runtimeDir 'bin\\java.exe'\n" +
                "      }\n" +
                "      Remove-Item $temp -Recurse -Force\n" +
                "    }\n" +
                "  }\n" +
                "  if (-Not (Test-Path $javaExe)) {\n" +
                "    throw 'Unable to prepare Java runtime. Install a JRE and re-launch.'\n" +
                "  }\n" +
                "  Ensure-Dependencies -LibDir $libDir\n" +
                "  $classpath = @()\n" +
                "  $classpath += (Join-Path $scriptDir 'SmartAttendanceCompanion.jar')\n" +
                "  Get-ChildItem -Path $libDir -Filter *.jar | ForEach-Object { $classpath += $_.FullName }\n" +
                "  $classpathString = $classpath -join ';'\n" +
                "  & \"$javaExe\" -cp $classpathString 'com.smartattendance.companion.CompanionApplication' @Args\n" +
                "  exit $LASTEXITCODE\n" +
                "} catch {\n" +
                "  Write-Error \"Companion failed to launch: $($_.Exception.Message)\"\n" +
                "  if ($host.Name -eq 'ConsoleHost') {\n" +
                "    [void](Read-Host 'Press Enter to close this window')\n" +
                "  }\n" +
                "  exit 1\n" +
                "}\n";
    }

    public record BuildArtifacts(String version,
                                 Path macArchive,
                                 Path windowsArchive,
                                 long macSize,
                                 long windowsSize,
                                 String macChecksum,
                                 String windowsChecksum,
                                 Instant publishedAt) {
    }
}
