# Stage 1: Build
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
RUN chmod +x mvnw
RUN ./mvnw dependency:go-offline -B

COPY src src
RUN ./mvnw package -DskipTests -B

# Stage 2: Run
FROM eclipse-temurin:17-jre
WORKDIR /app

# tesseract-ocr-eng provides the English language data (eng.traineddata).
# curl is needed for the Docker healthcheck.
RUN apt-get update && \
    apt-get install -y tesseract-ocr tesseract-ocr-eng libtesseract-dev curl && \
    rm -rf /var/lib/apt/lists/*

# Print detected tessdata location at build time for visibility.
RUN find /usr/share/tesseract-ocr -name "eng.traineddata" 2>/dev/null || echo "WARNING: eng.traineddata not found"

RUN groupadd -r appgroup && useradd -r -g appgroup appuser

COPY --from=build /app/target/*.jar app.jar
COPY entrypoint.sh entrypoint.sh

RUN mkdir -p /app/logs /app/gcp && \
    chmod +x /app/entrypoint.sh && \
    chown -R appuser:appgroup /app

USER appuser

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["/app/entrypoint.sh"]
