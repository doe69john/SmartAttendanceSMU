# =========================
# 1) Build stage
# =========================
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace

# Pre-copy Maven wrapper and pom files to leverage Docker cache
COPY backend/.mvn backend/.mvn
COPY backend/mvnw backend/mvnw
COPY backend/pom.xml backend/pom.xml
COPY backend/service/pom.xml backend/service/pom.xml
COPY backend/companion/pom.xml backend/companion/pom.xml

# Ensure wrapper is executable and download dependencies for the service module
RUN chmod +x backend/mvnw \
 && ./backend/mvnw -f backend/pom.xml -pl service -am dependency:go-offline -DskipTests

# Copy the full backend sources and build the service JAR
COPY backend backend
RUN ./backend/mvnw -f backend/pom.xml -pl service -am package -DskipTests

# =========================
# 2) Runtime stage
# =========================
FROM eclipse-temurin:21-jre-jammy AS runtime

# Install native libs required by OpenCV / JavaCPP
# (libgfortran/libgomp for OpenBLAS, and some basic X/GLIB bits that OpenCV depends on)
RUN apt-get update && apt-get install -y --no-install-recommends \
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

# Cloud Run will set PORT, default to 8080
ENV PORT=8080

# Reasonable JVM container tuning; you can tweak as needed
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

EXPOSE 8080

# Start Spring Boot and bind to Cloud Run's PORT
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar target/attendance-0.0.1-SNAPSHOT.jar --server.port=${PORT}"]
