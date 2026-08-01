# YouTube Shorts Generator - Docker Image

FROM openjdk:17-slim

# Install FFmpeg
RUN apt-get update && \
    apt-get install -y ffmpeg && \
    rm -rf /var/lib/apt/lists/*

# Create app directory
WORKDIR /app

# Copy application files
COPY target/youtube-shorts-generator-1.0.0-SNAPSHOT.jar /app/app.jar
COPY config /app/config

# Create directories
RUN mkdir -p /app/output /app/temp /app/logs

# Set environment variables
ENV JAVA_OPTS="-Xmx512m"

# Run the application
CMD ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar schedule"]
