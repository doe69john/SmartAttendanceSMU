# ---------- build stage ----------
    FROM maven:3.9-eclipse-temurin-21 AS build
    WORKDIR /build
    
    # Prime the Maven cache with just POMs first (faster incremental builds)
    COPY backend/pom.xml backend/pom.xml
    COPY backend/service/pom.xml backend/service/pom.xml
    # If companion is a separate Maven module with its own pom.xml, uncomment:
    # COPY backend/companion/pom.xml backend/companion/pom.xml
    
    # Go offline for deps for the service module
    RUN mvn -q -ntp -f backend/service/pom.xml -DskipTests dependency:go-offline
    
    # Now copy the full backend sources
    COPY backend backend
    
    # Package the Spring Boot service (fat jar)
    RUN mvn -ntp -f backend/service/pom.xml -DskipTests package
    
    
    # ---------- runtime stage ----------
    FROM eclipse-temurin:21-jre
    WORKDIR /app
    
    # Copy the packaged service JAR
    COPY --from=build /build/backend/service/target/*.jar /app/app.jar
    
    # Copy the entire backend tree (runtime expects /app/backend/... paths)
    COPY --from=build /build/backend /app/backend
    
    # Some code paths look for an absolute "/companion" — provide it
    RUN ln -s /app/backend/companion /companion || true
    
    # Reasonable JVM defaults for containers
    ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:InitialRAMPercentage=25"
    
    # Render expects you to listen on $PORT (default 10000) on 0.0.0.0
    EXPOSE 10000
    CMD ["sh","-c","java $JAVA_OPTS -Dserver.address=0.0.0.0 -Dserver.port=${PORT:-10000} -jar /app/app.jar"]
    