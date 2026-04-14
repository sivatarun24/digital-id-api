#!/bin/sh
# Auto-detect tessdata path if not explicitly set via env var.
# The path differs between Ubuntu versions:
#   Jammy (22.04) -> /usr/share/tesseract-ocr/4.00/tessdata
#   Noble (24.04) -> /usr/share/tesseract-ocr/5/tessdata
if [ -z "$TESSDATA_PATH" ]; then
    TESSDATA_PATH=$(find /usr/share/tesseract-ocr -name "eng.traineddata" 2>/dev/null | head -1 | xargs dirname 2>/dev/null)
    export TESSDATA_PATH
fi
echo "[Entrypoint] TESSDATA_PATH=${TESSDATA_PATH}"
echo "[Entrypoint] GCS_BUCKET_NAME=${GCS_BUCKET_NAME:-<not set, using local storage>}"
echo "[Entrypoint] GOOGLE_APPLICATION_CREDENTIALS=${GOOGLE_APPLICATION_CREDENTIALS:-<not set>}"

exec java \
    -XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -Djava.security.egd=file:/dev/./urandom \
    -jar /app/app.jar
