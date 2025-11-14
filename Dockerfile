# =========================
# 1) Build stage
# =========================
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace

# Copy minimal pom structure first for better Docker layer caching
COPY backend/pom.xml backend/pom.xml
COPY backend/service/pom.xml backend/service/pom.xml
COPY backend/companion/pom.xml backend/companion/pom.xml

# Pre-download dependencies for the service module using mvn (from the image)
RUN mvn -f backend/pom.xml -pl service -am dependency:go-offline -DskipTests

# Now copy the full backend sources
COPY backend backend

# Build only the service module (fat Spring Boot jar)
RUN mvn -f backend/pom.xml -pl service -am package -DskipTests


# =========================
# 2) Runtime stage
# =========================
FROM eclipse-temurin:21-jre-noble AS runtime

# Install native libs required by OpenCV / JavaCPP
RUN apt-get update && apt-get install -y --no-install-recommends \
      libopencv-core-dev \
      libopencv-imgproc-dev \
      libopencv-objdetect-dev \
      libopencv-videoio-dev \
      libopencv-java \
      libopenblas0 \
      libtbbmalloc2 \
      libdc1394-25 \
      libavcodec60 \
      libavformat60 \
      libswscale7 \
      libgtk2.0-0 \
      libcanberra-gtk-module \
      libgomp1 \
      libgfortran5 \
      libquadmath0 \
      libglib2.0-0 \
      libsm6 \
      libxext6 \
      libxrender1 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /workspace

# Copy the Spring Boot fat JAR from the build stage
COPY --from=build /workspace/backend/service/target/attendance-0.0.1-SNAPSHOT.jar ./target/attendance-0.0.1-SNAPSHOT.jar

# Provide the full backend workspace (service + companion + runtime assets)
COPY --from=build /workspace/backend ./backend

# Cloud Run will set PORT; default to 8080 for local runs
ENV PORT=8080

# Ensure JVM locates libopencv_java in default search paths
ENV LD_LIBRARY_PATH=/usr/lib/jni:$LD_LIBRARY_PATH

# JVM tuning for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

EXPOSE 8080

# Start Spring Boot, binding to the Cloud Run port
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar target/attendance-0.0.1-SNAPSHOT.jar --server.port=${PORT}"]
