package com.videogenerator.main;

import com.videogenerator.config.Configuration;
import com.videogenerator.model.UploadResult;
import com.videogenerator.scheduler.DailyScheduler;
import com.videogenerator.service.ContentGeneratorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Scanner;

/**
 * Main application entry point
 * YouTube Shorts Auto Generator
 */
public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    private static DailyScheduler scheduler;
    private static volatile boolean running = true;

    public static void main(String[] args) {
        logger.info("========================================");
        logger.info("YouTube Shorts Auto Generator v1.0.0");
        logger.info("========================================");

        try {
            // Load configuration
            Configuration config = Configuration.getInstance();
            logger.info("Configuration loaded successfully");

            // Validate configuration
            try {
                config.validate();
            } catch (IllegalStateException e) {
                logger.error("Configuration validation failed:");
                logger.error(e.getMessage());
                logger.error("Please update config/application.properties with your API keys");
                System.exit(1);
            }

            // Initialize content generator
            ContentGeneratorService contentGenerator = new ContentGeneratorService();
            logger.info("Content generator initialized");

            // Add shutdown hook to clean up resources
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutdown hook triggered");
                contentGenerator.shutdown();
            }));

            // Validate all services
            logger.info("Validating services...");
            if (!contentGenerator.validateServices()) {
                logger.error("Service validation failed. Please check your configuration and API keys.");
                System.exit(1);
            }

            // Check command line arguments
            if (args.length > 0) {
                handleCommandLineArgs(args, contentGenerator);
                return;
            }

            // Interactive mode
            showMenu(contentGenerator);

        } catch (Exception e) {
            logger.error("Fatal error", e);
            System.exit(1);
        }
    }

    /**
     * Handles command line arguments
     */
    private static void handleCommandLineArgs(String[] args, ContentGeneratorService contentGenerator) {
        String command = args[0].toLowerCase();

        try {
            switch (command) {
                case "generate":
                case "run":
                    logger.info("Running single video generation (original pipeline)...");
                    UploadResult result = contentGenerator.generateAndUploadShort();
                    logger.info("SUCCESS! Video uploaded: {}", result.getShortsUrl());
                    break;

                case "generate-ai":
                case "generate-tts":
                case "full":
                    logger.info("Running FULL AI pipeline (Niche → TTS → Video)...");
                    UploadResult aiResult = contentGenerator.generateAndUploadShortWithTTS();
                    logger.info("SUCCESS! AI-Generated Video: {}", aiResult.getShortsUrl());
                    break;

                case "schedule":
                case "daemon":
                    logger.info("Starting scheduler in daemon mode...");
                    startScheduler(contentGenerator);
                    break;

                case "validate":
                    logger.info("Validating services...");
                    boolean valid = contentGenerator.validateServices();
                    System.exit(valid ? 0 : 1);
                    break;

                case "help":
                    printHelp();
                    break;

                default:
                    logger.error("Unknown command: {}", command);
                    printHelp();
                    System.exit(1);
            }
        } catch (Exception e) {
            logger.error("Error executing command", e);
            System.exit(1);
        }
    }

    /**
     * Interactive menu
     */
    private static void showMenu(ContentGeneratorService contentGenerator) {
        Scanner scanner = new Scanner(System.in);

        while (running) {
            System.out.println("\n========================================");
            System.out.println("YouTube Shorts Auto Generator");
            System.out.println("========================================");
            System.out.println("1. Generate video (original pipeline)");
            System.out.println("2. Generate with AI (Niche + TTS + Viral)");
            System.out.println("3. Start automatic scheduler");
            System.out.println("4. Validate services");
            System.out.println("5. View status");
            System.out.println("6. Exit");
            System.out.println("========================================");
            System.out.print("Select option: ");

            try {
                int choice = scanner.nextInt();
                scanner.nextLine(); // Consume newline

                switch (choice) {
                    case 1:
                        generateNow(contentGenerator);
                        break;

                    case 2:
                        generateWithAI(contentGenerator);
                        break;

                    case 3:
                        startSchedulerInteractive(contentGenerator, scanner);
                        break;

                    case 4:
                        validateServices(contentGenerator);
                        break;

                    case 5:
                        showStatus(contentGenerator);
                        break;

                    case 6:
                        exit();
                        return;

                    default:
                        System.out.println("Invalid option. Please try again.");
                }
            } catch (Exception e) {
                logger.error("Error in menu", e);
                scanner.nextLine(); // Clear invalid input
            }
        }
    }

    /**
     * Generates video immediately (original pipeline)
     */
    private static void generateNow(ContentGeneratorService contentGenerator) {
        System.out.println("\n=== Generating YouTube Short (Original Pipeline) ===");
        System.out.println("This may take 3-5 minutes...\n");

        try {
            UploadResult result = contentGenerator.generateAndUploadShort();

            System.out.println("\n========================================");
            System.out.println("SUCCESS! Video generated and uploaded!");
            System.out.println("========================================");
            System.out.println("Video ID: " + result.getVideoId());
            System.out.println("URL: " + result.getUrl());
            System.out.println("Shorts URL: " + result.getShortsUrl());
            System.out.println("========================================");

        } catch (Exception e) {
            System.out.println("\nERROR: " + e.getMessage());
            logger.error("Error generating video", e);
        }
    }

    /**
     * Generates video with FULL AI pipeline (Niche Finder + TTS + Viral Ideas)
     */
    private static void generateWithAI(ContentGeneratorService contentGenerator) {
        System.out.println("\n=== Generating with FULL AI Pipeline ===");
        System.out.println("Pipeline: Niche Discovery → Viral Ideas → Script → TTS → Music → Video → Upload");
        System.out.println("This may take 5-8 minutes due to niche analysis...\n");

        try {
            UploadResult result = contentGenerator.generateAndUploadShortWithTTS();

            System.out.println("\n========================================");
            System.out.println("SUCCESS! AI-Powered Video uploaded!");
            System.out.println("========================================");
            System.out.println("Video ID: " + result.getVideoId());
            System.out.println("URL: " + result.getUrl());
            System.out.println("Shorts URL: " + result.getShortsUrl());
            System.out.println("========================================");
            System.out.println("\nThis video was created with:");
            System.out.println("- Automated niche discovery");
            System.out.println("- AI-generated viral content idea");
            System.out.println("- GPT-4 scripted voiceover");
            System.out.println("- ElevenLabs natural voice");
            System.out.println("- AI-generated music & video");
            System.out.println("========================================");

        } catch (Exception e) {
            System.out.println("\nERROR: " + e.getMessage());
            logger.error("Error in AI pipeline", e);
        }
    }

    /**
     * Starts scheduler in interactive mode
     */
    private static void startSchedulerInteractive(ContentGeneratorService contentGenerator, Scanner scanner) {
        if (scheduler != null && scheduler.isRunning()) {
            System.out.println("Scheduler is already running!");
            System.out.print("Stop scheduler? (y/n): ");
            String response = scanner.nextLine();
            if (response.equalsIgnoreCase("y")) {
                scheduler.stop();
                scheduler = null;
                System.out.println("Scheduler stopped.");
            }
            return;
        }

        Configuration config = Configuration.getInstance();
        String cron = config.getSchedulerCron();

        System.out.println("\n=== Starting Scheduler ===");
        System.out.println("Schedule: " + cron);
        System.out.println("Timezone: " + config.getSchedulerTimezone());

        scheduler = new DailyScheduler(contentGenerator);
        scheduler.start();

        long secondsUntilNext = scheduler.getTimeUntilNextExecution();
        System.out.println("Next execution in " + (secondsUntilNext / 60) + " minutes");
        System.out.println("\nScheduler is running in background.");
        System.out.println("Press any key to return to menu...");
        scanner.nextLine();
    }

    /**
     * Starts scheduler (for daemon mode)
     */
    private static void startScheduler(ContentGeneratorService contentGenerator) {
        scheduler = new DailyScheduler(contentGenerator);
        scheduler.start();

        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown signal received");
            if (scheduler != null) {
                scheduler.stop();
            }
        }));

        // Keep application running
        try {
            while (running) {
                Thread.sleep(60000); // Check every minute
            }
        } catch (InterruptedException e) {
            logger.info("Application interrupted");
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Validates all services
     */
    private static void validateServices(ContentGeneratorService contentGenerator) {
        System.out.println("\n=== Validating Services ===");
        boolean valid = contentGenerator.validateServices();

        if (valid) {
            System.out.println("✓ All services validated successfully!");
        } else {
            System.out.println("✗ Some services failed validation. Check logs for details.");
        }
    }

    /**
     * Shows current system status
     */
    private static void showStatus(ContentGeneratorService contentGenerator) {
        System.out.println("\n=== System Status ===");
        System.out.println("Service: Ready (thread-safe stateless design)");

        if (scheduler != null && scheduler.isRunning()) {
            long seconds = scheduler.getTimeUntilNextExecution();
            System.out.println("Scheduler: Running");
            System.out.println("Next execution: " + (seconds / 60) + " minutes");
        } else {
            System.out.println("Scheduler: Not running");
        }

        System.out.println("\nNote: Video status is logged during generation");
        System.out.println("========================================");
    }

    /**
     * Exits the application
     */
    private static void exit() {
        System.out.println("\nShutting down...");
        if (scheduler != null && scheduler.isRunning()) {
            scheduler.stop();
        }
        running = false;
        logger.info("Application stopped");
        System.out.println("Goodbye!");
    }

    /**
     * Prints help information
     */
    private static void printHelp() {
        System.out.println("\nYouTube Shorts Auto Generator v1.0.0");
        System.out.println("Usage: java -jar youtube-shorts-generator.jar [command]");
        System.out.println("\nCommands:");
        System.out.println("  generate       - Generate video (original pipeline: Music → Video → Upload)");
        System.out.println("  generate-ai    - Generate with FULL AI (Niche → TTS → Viral Ideas → Video)");
        System.out.println("  schedule       - Start automatic daily scheduler (daemon mode)");
        System.out.println("  validate       - Validate all services and API keys");
        System.out.println("  help           - Show this help message");
        System.out.println("\nNo arguments: Run in interactive mode");
        System.out.println("\nNew Features (Phase 1 & 2):");
        System.out.println("  - Niche Finder: Discovers trending, low-competition niches");
        System.out.println("  - Viral Idea Generator: Creates 10 SEO-optimized video concepts");
        System.out.println("  - Script Writer: Generates 150-200 word TTS-optimized scripts");
        System.out.println("  - Text-to-Speech: ElevenLabs natural voiceover generation");
        System.out.println("  - Audio Mixing: Professional voiceover + music blending");
    }
}
