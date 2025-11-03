# ---- build stage ----
    FROM maven:3.9-eclipse-temurin-21 AS build
    WORKDIR /build
    COPY pom.xml .
    RUN mvn -q -ntp -DskipTests dependency:go-offline
    COPY src ./src
    COPY runtime ./runtime
    RUN mvn -ntp -DskipTests package
    
    # ---- run stage ----
    FROM eclipse-temurin:21-jre
    WORKDIR /app
    COPY --from=build /build/target/*.jar app.jar
    
    # sensible JVM defaults for small containers
    ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:InitialRAMPercentage=25"
    
    # Render scans for a listener on $PORT and requires binding to 0.0.0.0
    # Default port on Render is 10000 if you don't override it in settings.
    EXPOSE 8080
    CMD ["sh","-c","java $JAVA_OPTS -Dserver.address=0.0.0.0 -Dserver.port=${PORT:-10000} -jar /app/app.jar"]
    