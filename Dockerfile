# =========================
# 1. Build stage
# =========================
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace

# Copy Maven wrapper and parent/backend poms to leverage layer caching
COPY backend/.mvn backend/.mvn
COPY backend/mvnw backend/mvnw
COPY backend/pom.xml backend/pom.xml
COPY backend/service/pom.xml backend/service/pom.xml
COPY backend/companion/pom.xml backend/companion/pom.xml

# Pre-download dependencies for the service module
RUN ./backend/mvnw -f backend/pom.xml -pl service -am dependency:go-offline -DskipTests

# Copy the full backend sources
COPY backend backend

# Build only the service module
RUN ./backend/mvnw -f backend/pom.xml -pl service -am package -DskipTests

# =========================
# 2. Runtime stage
# =========================
FROM eclipse-temurin:21-jre-jammy

# Use a non-root user for security
RUN useradd -u 1001 -m appuser

WORKDIR /app

# Copy the fat jar from the build stage
COPY --from=build /workspace/backend/service/target/attendance-0.0.1-SNAPSHOT.jar app.jar

# Copy runtime config and ensure directories exist for logs/models
COPY backend/runtime/config.properties ./backend/runtime/config.properties

# Create writable dirs for logs and data, owned by appuser
RUN mkdir -p /app/runtime/logs \
    && mkdir -p /app/backend/runtime/data \
    && chown -R appuser:appuser /app

USER appuser

# Cloud Run will inject PORT; we just expose 8080 for local use
EXPOSE 8080

# JVM memory tuning: respect container memory limits
ENV JAVA_TOOL_OPTIONS="-XX:InitialRAMPercentage=30 -XX:MaxRAMPercentage=80"

# Activate the "prod" profile by default (overrideable via env)
ENV SPRING_PROFILES_ACTIVE=prod

# Let Spring Boot read server.port from PORT (configured in application.properties)
ENTRYPOINT ["java", "-jar", "app.jar"]
