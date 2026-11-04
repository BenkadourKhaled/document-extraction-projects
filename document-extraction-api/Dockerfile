FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:21-jre-noble
ENV DEBIAN_FRONTEND=noninteractive
WORKDIR /app

LABEL org.opencontainers.image.title="Document Extraction API" \
      org.opencontainers.image.description="Local PDF and image text extraction API with Tesseract OCR (French, English, Arabic)" \
      org.opencontainers.image.licenses="UNLICENSED"

RUN apt-get update && apt-get install -y --no-install-recommends \
    tesseract-ocr tesseract-ocr-fra tesseract-ocr-eng tesseract-ocr-ara \
    libtesseract-dev libleptonica-dev curl fonts-dejavu-core \
    && rm -rf /var/lib/apt/lists/*

ENV TESSDATA_PREFIX=/usr/share/tesseract-ocr/5/tessdata

COPY --from=build /build/target/document-extraction-api-1.0.0.jar /app/app.jar

RUN useradd --system --uid 10001 appuser \
    && mkdir -p /app/data/db /app/data/storage \
    && chown -R appuser:appuser /app
USER appuser

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD curl -fsS http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java","-XX:MaxRAMPercentage=70.0","-jar","/app/app.jar"]
