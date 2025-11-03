# ---- base for tools ----
    FROM debian:bookworm-slim AS tools
    RUN apt-get update && apt-get install -y --no-install-recommends \
        ca-certificates curl unzip build-essential cmake git pkg-config \
        libgtk-3-dev libavcodec-dev libavformat-dev libswscale-dev \
        libtbb2 libtbb-dev libjpeg-dev libpng-dev libtiff-dev libdc1394-22-dev \
        openjdk-21-jdk maven && \
        rm -rf /var/lib/apt/lists/*
    
    # ---- build OpenCV 4.5.3 with Java ----
    FROM tools AS opencv
    WORKDIR /cv
    # Fetch sources
    RUN curl -L -o opencv-4.5.3.zip https://github.com/opencv/opencv/archive/4.5.3.zip && \
        unzip -q opencv-4.5.3.zip && mkdir -p opencv-4.5.3/build
    WORKDIR /cv/opencv-4.5.3/build
    # Build with Java enabled; produce libopencv_java453.so
    RUN cmake -D CMAKE_BUILD_TYPE=Release \
              -D CMAKE_INSTALL_PREFIX=/opt/opencv-4.5.3 \
              -D BUILD_SHARED_LIBS=ON \
              -D BUILD_TESTS=OFF -D BUILD_PERF_TESTS=OFF \
              -D BUILD_opencv_java=ON .. && \
        make -j"$(nproc)" && make install
    
    # ---- build your app ----
    FROM maven:3.9-eclipse-temurin-21 AS build
    WORKDIR /build
    COPY pom.xml .
    RUN mvn -q -ntp -DskipTests dependency:go-offline
    COPY src ./src
    COPY runtime ./runtime
    RUN mvn -ntp -DskipTests package
    
    # ---- runtime ----
    FROM eclipse-temurin:21-jre
    WORKDIR /app
    # Add the native OpenCV Java lib to a standard path
    COPY --from=opencv /opt/opencv-4.5.3/share/java/opencv4/opencv-453.jar /usr/local/share/java/
    COPY --from=opencv /opt/opencv-4.5.3/lib/libopencv_java453.so /usr/lib/
    # Your app
    COPY --from=build /build/target/*.jar app.jar
    
    ENV LD_LIBRARY_PATH="/usr/lib:${LD_LIBRARY_PATH}"
    ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:InitialRAMPercentage=25"
    
    # Bind as Render expects
    EXPOSE 10000
    CMD ["sh","-c","java $JAVA_OPTS -Djava.library.path=/usr/lib -Dserver.address=0.0.0.0 -Dserver.port=${PORT:-10000} -jar /app/app.jar"]
    