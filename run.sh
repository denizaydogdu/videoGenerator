#!/bin/bash

# YouTube Shorts Generator - Run Script

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "========================================="
echo "YouTube Shorts Generator"
echo "========================================="

# Check if jar exists
JAR_FILE="target/youtube-shorts-generator-1.0.0-SNAPSHOT.jar"

if [ ! -f "$JAR_FILE" ]; then
    echo -e "${YELLOW}JAR file not found. Building project...${NC}"
    mvn clean package

    if [ $? -ne 0 ]; then
        echo -e "${RED}Build failed!${NC}"
        exit 1
    fi
fi

# Run the application
if [ -z "$1" ]; then
    echo "Running in interactive mode..."
    java -jar "$JAR_FILE"
else
    echo "Running command: $1"
    java -jar "$JAR_FILE" "$1"
fi
