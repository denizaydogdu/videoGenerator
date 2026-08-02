package com.videogenerator.main;

import com.videogenerator.config.Configuration;
import com.videogenerator.scheduler.DailyScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shorts Factory entry point.
 *
 * Commands: generate <channelId> | resume <jobId> | publish <jobId>
 *         | serve | schedule | validate | help
 */
public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        logger.info("========================================");
        logger.info("Shorts Factory v2.0.0");
        logger.info("========================================");

        try {
            Configuration config = Configuration.getInstance();
            logger.info("Configuration loaded successfully");

            String command = args.length > 0 ? args[0].toLowerCase() : "help";
            switch (command) {
                case "generate", "resume" -> {
                    requireArg(args, command + " requires an argument");
                    // generate <channelId> [vaka ipucu...] — rotasyon için
                    String hint = args.length > 2
                            ? String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length))
                            : null;
                    runShortsFactory(command, args[1], hint, config);
                }
                case "publish" -> {
                    requireArg(args, "publish requires a jobId");
                    runPublish(args[1], config);
                }
                case "publish-meta" -> {
                    requireArg(args, "publish-meta requires a jobId");
                    runPublishMeta(args[1], args.length > 2 ? args[2] : "en", config);
                }
                case "serve" -> runBackoffice(config);
                case "schedule" -> runSchedule(config);
                case "validate" -> System.exit(runValidate(config) ? 0 : 1);
                case "help" -> printHelp();
                default -> {
                    System.out.println("Unknown command: " + command);
                    printHelp();
                    System.exit(1);
                }
            }
        } catch (Exception e) {
            logger.error("Fatal error", e);
            System.exit(1);
        }
    }

    private static void requireArg(String[] args, String message) {
        if (args.length < 2) {
            System.out.println("ERROR: " + message);
            printHelp();
            System.exit(1);
        }
    }

    /**
     * The scheduled task: generate one video per enabled channel via the
     * shorts-factory pipeline. Produces PENDING_REVIEW jobs — publishing
     * still requires human approval in the backoffice.
     */
    private static Runnable scheduledGenerationTask(Configuration config) {
        return () -> {
            try {
                var channelStore = new com.videogenerator.channel.ChannelStore(
                        java.nio.file.Path.of(config.getChannelsDir()));
                for (var profile : channelStore.loadEnabled()) {
                    try {
                        runPipelineJob("generate", profile.getChannelId(), config);
                    } catch (Exception e) {
                        logger.error("Scheduled generation failed for {}",
                                profile.getChannelId(), e);
                    }
                }
            } catch (Exception e) {
                logger.error("Scheduled generation sweep failed", e);
            }
        };
    }

    /**
     * Daemon mode: daily generation for all enabled channels.
     */
    private static void runSchedule(Configuration config) throws InterruptedException {
        DailyScheduler scheduler = new DailyScheduler(scheduledGenerationTask(config));
        scheduler.start();
        Runtime.getRuntime().addShutdownHook(new Thread(scheduler::stop));
        System.out.println("========================================");
        System.out.println("Scheduler running (daily generation, review via 'serve')");
        System.out.println("Next run in " + scheduler.getTimeUntilNextExecution() / 60 + " min");
        System.out.println("Ctrl-C to stop");
        System.out.println("========================================");
        Thread.currentThread().join();
    }

    /**
     * Checks local prerequisites without spending any API credits.
     */
    private static boolean runValidate(Configuration config) {
        boolean ok = true;
        var ffmpeg = new com.videogenerator.processor.FFmpegWrapper();
        if (ffmpeg.validateFFmpegInstallation()) {
            System.out.println("[ok] ffmpeg: " + ffmpeg.getFfmpegPath());
        } else {
            System.out.println("[FAIL] ffmpeg not found (ffmpeg.path=" + ffmpeg.getFfmpegPath() + ")");
            ok = false;
        }
        if (ffmpeg.validateFfprobeInstallation()) {
            System.out.println("[ok] ffprobe");
        } else {
            System.out.println("[FAIL] ffprobe not found (ffprobe.path)");
            ok = false;
        }
        String openAi = config.getOpenAiApiKey();
        if (openAi != null && !openAi.isEmpty() && !openAi.contains("YOUR_")) {
            System.out.println("[ok] OpenAI API key configured");
        } else {
            System.out.println("[FAIL] OpenAI API key missing (openai.api.key)");
            ok = false;
        }
        String eleven = config.getTtsApiKey();
        if (eleven != null && !eleven.isEmpty() && !eleven.contains("YOUR_")) {
            System.out.println("[ok] ElevenLabs API key configured");
        } else {
            System.out.println("[FAIL] ElevenLabs API key missing (tts.api.key)");
            ok = false;
        }
        try {
            var channels = new com.videogenerator.channel.ChannelStore(
                    java.nio.file.Path.of(config.getChannelsDir())).loadEnabled();
            if (channels.isEmpty()) {
                System.out.println("[warn] No enabled channel profiles in "
                        + config.getChannelsDir() + "/ (copy the .example file)");
            } else {
                System.out.println("[ok] " + channels.size() + " enabled channel(s): "
                        + channels.stream()
                                .map(com.videogenerator.channel.ChannelProfile::getChannelId)
                                .toList());
            }
        } catch (Exception e) {
            System.out.println("[FAIL] Channel profiles unreadable: " + e.getMessage());
            ok = false;
        }
        System.out.println(ok ? "Validation OK" : "Validation FAILED");
        return ok;
    }

    /** Yayınlanmış bir işin tek dilini IG+FB'ye geriye dönük gönderir. */
    private static void runPublishMeta(String jobId, String lang, Configuration config) {
        try {
            String metaToken = config.get("meta.access.token", "");
            if (metaToken.isBlank()) {
                System.out.println("ERROR: meta.access.token not configured");
                System.exit(1);
            }
            var meta = new com.videogenerator.publish.MetaApiClient(
                    new com.videogenerator.publish.GraphHttp(), metaToken,
                    config.get("meta.page.id", ""), config.get("meta.ig.user.id", ""),
                    5000);
            var publishers = java.util.Map.<String, com.videogenerator.publish.Publisher>of(
                    "INSTAGRAM", new com.videogenerator.publish.MetaReelsPublisher("INSTAGRAM", meta),
                    "FACEBOOK", new com.videogenerator.publish.MetaReelsPublisher("FACEBOOK", meta));
            var added = com.videogenerator.publish.MetaBackfill.run(
                    new com.videogenerator.job.JobStore(
                            java.nio.file.Path.of(config.getJobsDir())),
                    new com.videogenerator.channel.ChannelStore(
                            java.nio.file.Path.of(config.getChannelsDir())),
                    jobId, lang, publishers);
            if (added.isEmpty()) {
                System.out.println("Nothing to do — already published on IG+FB");
            }
            added.forEach(p -> System.out.println(p.getPlatform() + " -> " + p.getUrl()));
        } catch (Exception e) {
            logger.error("publish-meta failed", e);
            System.out.println("ERROR: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void runPublish(String jobId, Configuration config) {
        try {
            var job = buildPublishService(config).publishApproved(jobId);
            System.out.println("Published: " + job.getJobId() + " -> " + job.getStatus());
        } catch (Exception e) {
            logger.error("Publish failed", e);
            System.out.println("ERROR: " + e.getMessage());
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
        var publishers = new java.util.HashMap<String, com.videogenerator.publish.Publisher>();
        publishers.put("YOUTUBE", youtube);
        String metaToken = config.get("meta.access.token", "");
        if (!metaToken.isBlank()) {
            var meta = new com.videogenerator.publish.MetaApiClient(
                    new com.videogenerator.publish.GraphHttp(), metaToken,
                    config.get("meta.page.id", ""), config.get("meta.ig.user.id", ""),
                    5000);
            String metaLang = config.get("meta.publish.lang", "en");
            publishers.put("INSTAGRAM",
                    new com.videogenerator.publish.MetaReelsPublisher("INSTAGRAM", meta, metaLang));
            publishers.put("FACEBOOK",
                    new com.videogenerator.publish.MetaReelsPublisher("FACEBOOK", meta, metaLang));
        }
        return new com.videogenerator.publish.PublishService(
                jobStore, channelStore, java.util.Map.copyOf(publishers),
                new com.videogenerator.publish.UploadCounter(
                        java.nio.file.Path.of(config.getCostsDir())),
                config.getYouTubeDailyLimit());
    }

    /**
     * Starts the backoffice review console. Generation requests from the UI
     * run on a single-thread executor (serialized: concurrent runs of the
     * same channel would duplicate spend).
     */
    /**
     * İzlenme sağlayıcıları. YouTube istemcisi TEMBEL kurulur: serve
     * açılışında OAuth tarayıcı akışı tetiklenmesin — ilk istatistik
     * isteğinde kurulur ve önbellenir.
     */
    private static com.videogenerator.web.StatsCollector buildStatsCollector(
            Configuration config) {
        var providers = new java.util.HashMap<String, com.videogenerator.web.StatsCollector.ViewsProvider>();
        var ytHolder = new java.util.concurrent.atomic.AtomicReference<com.videogenerator.api.YouTubeApiClient>();
        providers.put("YOUTUBE", url -> {
            String id = com.videogenerator.web.StatsCollector.youtubeId(url);
            if (id == null) {
                return null;
            }
            var client = ytHolder.get();
            if (client == null) {
                client = new com.videogenerator.api.YouTubeApiClient("tokens/truecrime-en");
                ytHolder.set(client);
            }
            return client.getViewCount(id);
        });
        String metaToken = config.get("meta.access.token", "");
        if (!metaToken.isBlank()) {
            var meta = new com.videogenerator.publish.MetaApiClient(
                    new com.videogenerator.publish.GraphHttp(), metaToken,
                    config.get("meta.page.id", ""), config.get("meta.ig.user.id", ""), 0);
            providers.put("INSTAGRAM", meta::igViewsByPermalink);
            providers.put("FACEBOOK", url -> {
                String id = com.videogenerator.web.StatsCollector.fbReelId(url);
                return id == null ? null : meta.fbReelViews(id);
            });
        }
        return new com.videogenerator.web.StatsCollector(providers);
    }

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
                    service, launcher, publishLauncher, config.getBackofficePort())
                    .withStats(buildStatsCollector(config));
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
    private static void runShortsFactory(String command, String target,
                                         String topicHint, Configuration config) {
        try {
            runPipelineJob(command, target, topicHint, config);
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
        runPipelineJob(command, target, null, config);
    }

    private static void runPipelineJob(String command, String target,
                                       String topicHint, Configuration config)
            throws Exception {
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
                new com.videogenerator.service.IdeaGenerator(gptClient),
                config.getBoolean("music.enabled", true))
                .withLocalMusicDir(java.nio.file.Path.of(
                        config.get("music.local.dir", "assets/music")))
                .withTopicHint(topicHint);

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

    private static void printHelp() {
        System.out.println("\nShorts Factory v2.0.0");
        System.out.println("Usage: java -jar youtube-shorts-generator.jar <command>");
        System.out.println("\nCommands:");
        System.out.println("  generate <channelId> [topic hint] - Generate: story -> images -> TTS -> render (PENDING_REVIEW)");
        System.out.println("  resume <jobId>       - Resume an interrupted/failed job");
        System.out.println("  publish <jobId>      - Publish an APPROVED/PUBLISHING job's variants");
        System.out.println("  serve                - Start the backoffice review console (localhost)");
        System.out.println("  schedule             - Daemon: daily generation for all enabled channels");
        System.out.println("  validate             - Check ffmpeg, API keys and channel profiles");
        System.out.println("  help                 - Show this help");
    }
}
