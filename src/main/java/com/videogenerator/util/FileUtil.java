package com.videogenerator.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * File utility class for handling file operations
 */
public class FileUtil {
    private static final Logger logger = LoggerFactory.getLogger(FileUtil.class);
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    /**
     * Downloads a file from a URL to a local path
     */
    public static File downloadFile(String urlStr, String destinationPath) throws IOException {
        logger.info("Downloading file from {} to {}", urlStr, destinationPath);

        URL url = new URL(urlStr);
        File outputFile = new File(destinationPath);

        // Create parent directories if they don't exist
        File parentDir = outputFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        try (ReadableByteChannel rbc = Channels.newChannel(url.openStream());
             FileOutputStream fos = new FileOutputStream(outputFile)) {
            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
        }

        logger.info("File downloaded successfully: {} ({} bytes)", outputFile.getName(), outputFile.length());
        return outputFile;
    }

    /**
     * Reads entire file content as string
     */
    public static String readFileAsString(String filePath) throws IOException {
        return new String(Files.readAllBytes(Paths.get(filePath)));
    }

    /**
     * Writes string content to file
     */
    public static void writeStringToFile(String content, String filePath) throws IOException {
        File file = new File(filePath);
        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write(content);
        }

        logger.debug("Written {} bytes to {}", content.length(), filePath);
    }

    /**
     * Generates a unique filename with timestamp
     */
    public static String generateTimestampedFilename(String prefix, String extension) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        return String.format("%s_%s.%s", prefix, timestamp, extension);
    }

    /**
     * Ensures a directory exists, creates it if it doesn't
     */
    public static void ensureDirectoryExists(String dirPath) {
        File dir = new File(dirPath);
        if (!dir.exists()) {
            if (dir.mkdirs()) {
                logger.info("Created directory: {}", dirPath);
            } else {
                logger.warn("Failed to create directory: {}", dirPath);
            }
        }
    }

    /**
     * Deletes a file
     */
    public static boolean deleteFile(String filePath) {
        File file = new File(filePath);
        if (file.exists()) {
            boolean deleted = file.delete();
            if (deleted) {
                logger.debug("Deleted file: {}", filePath);
            } else {
                logger.warn("Failed to delete file: {}", filePath);
            }
            return deleted;
        }
        return false;
    }

    /**
     * Deletes all files in a directory older than specified days
     */
    public static int cleanOldFiles(String directoryPath, int daysOld) {
        File directory = new File(directoryPath);
        if (!directory.exists() || !directory.isDirectory()) {
            return 0;
        }

        long cutoffTime = System.currentTimeMillis() - (daysOld * 24L * 60 * 60 * 1000);
        int deletedCount = 0;

        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.lastModified() < cutoffTime) {
                    if (file.delete()) {
                        deletedCount++;
                        logger.debug("Deleted old file: {}", file.getName());
                    }
                }
            }
        }

        if (deletedCount > 0) {
            logger.info("Cleaned {} old files from {}", deletedCount, directoryPath);
        }

        return deletedCount;
    }

    /**
     * Gets file size in bytes
     */
    public static long getFileSize(String filePath) {
        File file = new File(filePath);
        return file.exists() ? file.length() : -1;
    }

    /**
     * Gets file size in human-readable format
     */
    public static String getFileSizeFormatted(String filePath) {
        long size = getFileSize(filePath);
        if (size < 0) {
            return "N/A";
        }

        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.2f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", size / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", size / (1024.0 * 1024 * 1024));
        }
    }

    /**
     * Moves a file from source to destination
     */
    public static void moveFile(String sourcePath, String destinationPath) throws IOException {
        Path source = Paths.get(sourcePath);
        Path destination = Paths.get(destinationPath);

        // Create parent directories if needed
        File destFile = new File(destinationPath);
        File parentDir = destFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        logger.debug("Moved file from {} to {}", sourcePath, destinationPath);
    }

    /**
     * Validates if a file exists and is readable
     */
    public static boolean isValidFile(String filePath) {
        File file = new File(filePath);
        return file.exists() && file.isFile() && file.canRead();
    }

    /**
     * Gets file extension
     */
    public static String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0 && lastDot < filename.length() - 1) {
            return filename.substring(lastDot + 1).toLowerCase();
        }
        return "";
    }

    /**
     * Validates file format
     */
    public static boolean isValidFormat(String filePath, String... validExtensions) {
        String extension = getFileExtension(filePath);
        for (String validExt : validExtensions) {
            if (extension.equals(validExt.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}
