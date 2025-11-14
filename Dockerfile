# =========================
# 1. Build stage
# =========================
FROM maven:3.9-eclipse-temurin-21 AS build

# Work in /workspace
WORKDIR /workspace

# Copy minimal pom structure first for better Docker layer caching
COPY backend/pom.xml backend/pom.xml
COPY backend/service/pom.xml backend/service/pom.xml
COPY backend/companion/pom.xml backend/companion/pom.xml

# Pre-download dependencies for the service module using mvn from the image
RUN mvn -f backend/pom.xml -pl service -am dependency:go-offline -DskipTests

# Now copy the full backend sources
COPY backend backend

# Build only the service module (fat Spring Boot jar)
RUN mvn -f backend/pom.xml -pl service -am package -DskipTests

# =========================
# 2. Runtime stage
# =========================
FROM eclipse-temurin:21-jre-jammy

# Create a non-root user for security
RUN useradd -u 1001 -m appuser

# App will live in /app
WORKDIR /app

# Copy the built Spring Boot jar from the build stage
COPY --from=build /workspace/backend/service/target/attendance-0.0.1-SNAPSHOT.jar app.jar

# Copy runtime config so AttendanceProperties can find backend/runtime/config.properties
COPY backend/runtime/config.properties ./backend/runtime/config.properties

# Create writable directories for logs and data, then fix ownership
RUN mkdir -p /app/runtime/logs \
    && mkdir -p /app/backend/runtime/data \
    && chown -R appuser:appuser /app

# Run as non-root
USER appuser

# Expose 8080 for local runs (Cloud Run still injects PORT)
EXPOSE 8080

# JVM memory tuning to respect container limits
ENV JAVA_TOOL_OPTIONS="-XX:InitialRAMPercentage=30 -XX:MaxRAMPercentage=80"

# Use prod profile by default (override via env if needed)
ENV SPRING_PROFILES_ACTIVE=prod

# Start the Spring Boot application
ENTRYPOINT ["java", "-jar", "app.jar"]
