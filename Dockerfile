# ---------- build stage ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Speed up incremental builds by priming deps first
COPY backend/pom.xml backend/pom.xml
COPY backend/service/pom.xml backend/service/pom.xml
RUN mvn -q -ntp -f backend/service/pom.xml -DskipTests dependency:go-offline

# Now copy full backend sources and build the app jar
COPY backend backend
RUN mvn -ntp -f backend/service/pom.xml -DskipTests package

# ---------- runtime stage ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

# Native libraries required by OpenCV/JavaCV at runtime
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        libgl1 \
        libglib2.0-0 \
        libsm6 \
        libxext6 \
        libxrender1 \
    && rm -rf /var/lib/apt/lists/*

# App jar
COPY --from=build /build/backend/service/target/*.jar /app/app.jar

# Ship the entire backend tree (your code expects /app/backend/...)
COPY --from=build /build/backend /app/backend

# Make the companion module visible at BOTH paths:
#  - /app/backend/companion   (already present from the copy above)
#  - /app/companion           (some utilities probe here)
RUN ln -s /app/backend/companion /app/companion \
    || (mkdir -p /app/companion && cp -a /app/backend/companion/. /app/companion)

# JVM defaults suitable for small instances
ENV JAVA_OPTS="-XX:InitialRAMPercentage=15 -XX:MaxRAMPercentage=60 -XX:+UseSerialGC -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=64m -XX:+ExitOnOutOfMemoryError"

# Render scans for a listener on $PORT and expects 0.0.0.0; default is 10000
EXPOSE 10000
CMD ["sh","-c","java $JAVA_OPTS -Dserver.address=0.0.0.0 -Dserver.port=${PORT:-10000} -jar /app/app.jar"]
