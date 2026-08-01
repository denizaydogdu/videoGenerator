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

            // Shorts-factory commands run WITHOUT the legacy service stack
            // (no Suno/Sora validation): generate <channelId> | resume <jobId> | serve
            if (args.length >= 2 && ("generate".equalsIgnoreCase(args[0])
                    || "resume".equalsIgnoreCase(args[0]))) {
                runShortsFactory(args[0].toLowerCase(), args[1], config);
                return;
            }
            if (args.length >= 2 && "publish".equalsIgnoreCase(args[0])) {
                try {
                    var job = buildPublishService(config).publishApproved(args[1]);
                    System.out.println("Published: " + job.getJobId()
                            + " -> " + job.getStatus());
                } catch (Exception e) {
                    logger.error("Publish failed", e);
                    System.out.println("ERROR: " + e.getMessage());
                    System.exit(1);
                }
                return;
            }
            if (args.length >= 1 && "serve".equalsIgnoreCase(args[0])) {
                runBackoffice(config);
                return;
            }

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
     * Builds the publish service with the real YouTube publisher.
     * OAuth happens lazily on first upload per channel.
     */
    private static com.videogenerator.publish.PublishService buildPublishService(
            Configuration config) {
        var jobStore = new com.videogenerator.job.JobStore(
                java.nio.file.Path.of(config.getJobsDir()));
        var channelStore = new com.videogenerator.channel.ChannelStore(
                java.nio.file.Path.of(config.getChannelsDir()));
        var youtube = new com.videogenerator.publish.YouTubePublisher(profile -> {
            var client = new com.videogenerator.api.YouTubeApiClient(
                    "tokens/" + profile.getChannelId());
            return client::uploadVideo;
        });
        return new com.videogenerator.publish.PublishService(
                jobStore, channelStore, java.util.Map.of("YOUTUBE", youtube));
    }

    /**
     * Starts the backoffice review console. Generation requests from the UI
     * run on a single-thread executor (serialized: concurrent runs of the
     * same channel would duplicate spend).
     */
    private static void runBackoffice(Configuration config) {
        try {
            var jobStore = new com.videogenerator.job.JobStore(
                    java.nio.file.Path.of(config.getJobsDir()));
            var channelStore = new com.videogenerator.channel.ChannelStore(
                    java.nio.file.Path.of(config.getChannelsDir()));
            var costTracker = new com.videogenerator.job.CostTracker(
                    java.nio.file.Path.of(config.getCostsDir()));
            var service = new com.videogenerator.web.JobService(
                    jobStore, channelStore, costTracker, config.getMonthlyBudgetUsd());

            var pipelineExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();
            com.videogenerator.web.BackofficeServer.JobLauncher launcher = channelId ->
                    pipelineExecutor.submit(() -> {
                        try {
                            runPipelineJob("generate", channelId, config);
                        } catch (Exception e) {
                            // Sunucu ayakta kalır; iş FAILED olarak job.json'da görünür
                            logger.error("Queued generation failed for {}", channelId, e);
                        }
                    });

            // Yayın, üretimden AYRI kuyrukta: uzun bir üretim onaylı işi
            // bekletmesin, yavaş bir upload üretimi bloklamasın
            var publishExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();
            var publishService = buildPublishService(config);
            com.videogenerator.web.BackofficeServer.JobLauncher publishLauncher = jobId ->
                    publishExecutor.submit(() -> {
                        try {
                            publishService.publishApproved(jobId);
                        } catch (Exception e) {
                            // İş PUBLISHING+error olarak kalır; publish <jobId> ile devam
                            logger.error("Async publish failed for {}", jobId, e);
                        }
                    });

            var server = new com.videogenerator.web.BackofficeServer(
                    service, launcher, publishLauncher, config.getBackofficePort());
            int port = server.start();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                server.stop();
                pipelineExecutor.shutdownNow();
                publishExecutor.shutdownNow();
            }));

            System.out.println("========================================");
            System.out.println("Backoffice: http://127.0.0.1:" + port);
            System.out.println("Ctrl-C to stop");
            System.out.println("========================================");
            Thread.currentThread().join(); // Ctrl-C'ye kadar blokla
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error("Backoffice failed to start", e);
            System.exit(1);
        }
    }

    /**
     * CLI wrapper: runs one pipeline job and exits non-zero on failure.
     */
    private static void runShortsFactory(String command, String target, Configuration config) {
        try {
            runPipelineJob(command, target, config);
        } catch (Exception e) {
            logger.error("Shorts factory {} failed", command, e);
            System.out.println("ERROR: " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Builds the shorts-factory pipeline with real clients and runs one job.
     * Throws on failure (no System.exit) so it is safe inside the backoffice
     * executor.
     */
    private static void runPipelineJob(String command, String target, Configuration config)
            throws Exception {
        {
            var gptClient = new com.videogenerator.api.OpenAiGptClient();
            var elevenLabs = new com.videogenerator.api.ElevenLabsClient();
            var ffmpeg = new com.videogenerator.processor.FFmpegWrapper();

            var jobStore = new com.videogenerator.job.JobStore(
                    java.nio.file.Path.of(config.getJobsDir()));
            var channelStore = new com.videogenerator.channel.ChannelStore(
                    java.nio.file.Path.of(config.getChannelsDir()));
            var costTracker = new com.videogenerator.job.CostTracker(
                    java.nio.file.Path.of(config.getCostsDir()));
            var budgetGuard = new com.videogenerator.job.BudgetGuard(
                    costTracker, config.getMonthlyBudgetUsd());

            com.videogenerator.job.JobPipeline.TtsEngine ttsEngine =
                    (text, voiceId, audioOut, alignOut) -> {
                        var voice = new com.videogenerator.model.VoiceConfig();
                        voice.setVoiceId(voiceId);
                        voice.setModel(config.getTtsModel());
                        return elevenLabs.generateWithTimestamps(text, voice, audioOut, alignOut);
                    };

            var pipeline = new com.videogenerator.job.JobPipeline(
                    jobStore, channelStore, gptClient,
                    new com.videogenerator.api.ImageApiClient(),
                    new com.videogenerator.api.MusicApiClient(),
                    ttsEngine,
                    new com.videogenerator.job.DefaultRenderEngine(
                            new com.videogenerator.processor.AudioProcessor(),
                            new com.videogenerator.processor.KenBurnsRenderer(ffmpeg)),
                    budgetGuard, costTracker,
                    new com.videogenerator.service.IdeaGenerator(gptClient));

            com.videogenerator.job.Job job = "resume".equals(command)
                    ? pipeline.resume(target)
                    : pipeline.run(target);

            System.out.println("========================================");
            System.out.println("Job " + job.getJobId() + " -> " + job.getStatus());
            System.out.println("Variants: " + job.getVariants().size()
                    + "  Cost: $" + String.format("%.2f", job.getCost().total()));
            System.out.println("Review dir: " + jobStore.dirFor(job.getJobId()));
            System.out.println("========================================");
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
        System.out.println("  generate <channelId> - Shorts factory: story→images→TTS→render (PENDING_REVIEW)");
        System.out.println("  resume <jobId>       - Resume an interrupted/failed shorts-factory job");
        System.out.println("  serve                - Start the backoffice review console (localhost)");
        System.out.println("  publish <jobId>      - Publish an APPROVED/PUBLISHING job's variants");
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
