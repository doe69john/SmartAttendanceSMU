# ---- build stage ----
    FROM maven:3.9-eclipse-temurin-21 AS build
    WORKDIR /build
    
    # copy only what's needed for the service module
    COPY pom.xml .
    COPY src ./src
    COPY runtime ./runtime
    
    # build a Spring Boot fat jar (skip tests for faster deploys)
    RUN mvn -ntp -DskipTests package
    
    # ---- run stage ----
    FROM eclipse-temurin:21-jre
    WORKDIR /app
    
    # copy the fat jar built above
    COPY --from=build /build/target/*.jar app.jar
    
    # Spring Boot reads server.port from env; Koyeb/Render set PORT for you.
    # We also cap JVM heap relative to container memory.
    ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0"
    
    EXPOSE 8080
    CMD ["sh", "-c", "java $JAVA_OPTS -Dserver.port=${PORT:-8080} -jar /app/app.jar"]
    