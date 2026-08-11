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
                case "tiktok-auth" -> runTikTokAuth(config);
                case "pinterest-auth" -> runPinterestAuth(config);
                case "velzon-x-auth" -> runVelzonXAuth(config);
                case "pinterest-batch" -> {
                    requireArg(args, "pinterest-batch requires: <count> <niche prompt...>");
                    if (args.length < 3) {
                        System.out.println("ERROR: usage: pinterest-batch <count> <niche prompt...>");
                        System.exit(1);
                    }
                    runPinterestBatch(Integer.parseInt(args[1]),
                            String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)),
                            config);
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

    /** Config'e göre sandbox veya prod TikTok istemcisi kurar. */
    private static com.videogenerator.publish.TikTokApiClient buildTikTokClient(
            Configuration config) {
        boolean sandbox = config.getBoolean("tiktok.use.sandbox", true);
        String key = sandbox ? config.get("tiktok.sandbox.client.key", "")
                : config.get("tiktok.client.key", "");
        String secret = sandbox ? config.get("tiktok.sandbox.client.secret", "")
                : config.get("tiktok.client.secret", "");
        if (key.isBlank()) {
            return null;
        }
        return new com.videogenerator.publish.TikTokApiClient(
                new com.videogenerator.publish.TikTokHttp(), key, secret,
                config.get("tiktok.redirect.uri", ""),
                java.nio.file.Path.of("config/tokens/tiktok-"
                        + (sandbox ? "sandbox" : "prod") + ".json"),
                5000);
    }

    /**
     * Tek seferlik TikTok yetkilendirmesi: adresi yazdırır, kullanıcı
     * tarayıcıda onaylar, callback sayfasındaki kodu terminale yapıştırır.
     */
    private static void runTikTokAuth(Configuration config) {
        try {
            var client = buildTikTokClient(config);
            if (client == null) {
                System.out.println("ERROR: tiktok client key not configured");
                System.exit(1);
            }
            String url = client.authorizationUrl("st" + System.currentTimeMillis());
            System.out.println("========================================");
            System.out.println("1) Bu adresi tarayıcıda aç ve TikTok hesabıyla onayla:");
            System.out.println(url);
            System.out.println("2) Açılan sayfadaki kodu buraya yapıştır ve Enter'a bas:");
            System.out.print("> ");
            String code = new java.util.Scanner(System.in).nextLine().trim();
            client.exchangeCode(code);
            System.out.println("TikTok yetkilendirme TAMAM — token kaydedildi.");
        } catch (Exception e) {
            logger.error("tiktok-auth failed", e);
            System.out.println("ERROR: " + e.getMessage());
            System.exit(1);
        }
    }

    private static com.videogenerator.pinterest.PinterestApiClient buildPinterestClient(
            Configuration config) {
        String key = config.get("pinterest.client.id", "");
        if (key.isBlank()) {
            return null;
        }
        return new com.videogenerator.pinterest.PinterestApiClient(
                new com.videogenerator.pinterest.PinterestHttp(), key,
                config.get("pinterest.client.secret", ""),
                config.get("pinterest.redirect.uri", ""),
                java.nio.file.Path.of("config/tokens/pinterest.json"),
                0);
    }

    /** Tek seferlik Pinterest yetkilendirmesi — tiktok-auth ile aynı akış. */
    private static void runPinterestAuth(Configuration config) {
        try {
            var client = buildPinterestClient(config);
            if (client == null) {
                System.out.println("ERROR: pinterest.client.id not configured");
                System.exit(1);
            }
            String url = client.authorizationUrl("st" + System.currentTimeMillis());
            System.out.println("========================================");
            System.out.println("1) Bu adresi tarayıcıda aç ve Pinterest hesabıyla onayla:");
            System.out.println(url);
            System.out.println("2) Açılan sayfadaki kodu buraya yapıştır ve Enter'a bas:");
            System.out.print("> ");
            String code = new java.util.Scanner(System.in).nextLine().trim();
            client.exchangeCode(code);
            System.out.println("Pinterest yetkilendirme TAMAM — token kaydedildi.");
        } catch (Exception e) {
            logger.error("pinterest-auth failed", e);
            System.out.println("ERROR: " + e.getMessage());
            System.exit(1);
        }
    }

    private static com.videogenerator.velzon.XApiClient buildXApiClient(Configuration config) {
        String key = config.get("x.client.id", "");
        if (key.isBlank()) {
            return null;
        }
        return new com.videogenerator.velzon.XApiClient(
                new com.videogenerator.velzon.XHttp(), key,
                config.get("x.client.secret", ""),
                config.get("x.redirect.uri", ""),
                java.nio.file.Path.of("config/tokens/velzon-x.json"));
    }

    /** Tek seferlik X yetkilendirmesi — PKCE dahil, tiktok-auth ile aynı akış şekli. */
    private static void runVelzonXAuth(Configuration config) {
        try {
            var client = buildXApiClient(config);
            if (client == null) {
                System.out.println("ERROR: x.client.id not configured");
                System.exit(1);
            }
            String url = client.authorizationUrl("st" + System.currentTimeMillis());
            System.out.println("========================================");
            System.out.println("1) Bu adresi tarayıcıda aç ve X hesabıyla onayla:");
            System.out.println(url);
            System.out.println("2) Açılan sayfadaki kodu buraya yapıştır ve Enter'a bas:");
            System.out.print("> ");
            String code = new java.util.Scanner(System.in).nextLine().trim();
            client.exchangeCode(code);
            System.out.println("X yetkilendirme TAMAM — token kaydedildi.");
        } catch (Exception e) {
            logger.error("velzon-x-auth failed", e);
            System.out.println("ERROR: " + e.getMessage());
            System.exit(1);
        }
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    /**
     * Pinterest pin fikirleri üretir (manuel doğrulama aşaması — otomatik
     * yayın YOK). Çıktı: output/pinterest/batch-<epoch>/pin-NN.png +
     * manifest.json (başlık + açıklama, link yok).
     */
    private static void runPinterestBatch(int count, String niche, Configuration config) {
        try {
            var gpt = new com.videogenerator.api.OpenAiGptClient();
            var img = new com.videogenerator.api.ImageApiClient();
            var generator = new com.videogenerator.pinterest.PinterestBatchGenerator(gpt, img);
            var outDir = java.nio.file.Path.of("output/pinterest",
                    "batch-" + System.currentTimeMillis());
            var pins = generator.generateBatch(niche, count, outDir);
            System.out.println("========================================");
            System.out.println("Pinterest batch ready: " + pins.size() + " pins");
            System.out.println("Dir: " + outDir.toAbsolutePath());
            System.out.println("Not: linksiz — Amazon Associates onayindan sonra");
            System.out.println("SiteStripe ile gercek urun linki eklenmeli.");
            System.out.println("========================================");
        } catch (Exception e) {
            logger.error("pinterest-batch failed", e);
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
            // Kanalın kendi Meta hesabı varsa (channels/<id>.json meta bloğu)
            // ona özel istemci; eksik alanlar global config'ten tamamlanır
            var metaCache = new java.util.concurrent.ConcurrentHashMap<String, com.videogenerator.publish.MetaApiClient>();
            com.videogenerator.publish.MetaReelsPublisher.ClientResolver resolver = profile -> {
                var spec = profile.getMeta();
                return metaCache.computeIfAbsent(profile.getChannelId(), id ->
                        new com.videogenerator.publish.MetaApiClient(
                                new com.videogenerator.publish.GraphHttp(),
                                orDefault(spec.getAccessToken(), metaToken),
                                orDefault(spec.getPageId(), config.get("meta.page.id", "")),
                                orDefault(spec.getIgUserId(), config.get("meta.ig.user.id", "")),
                                5000));
            };
            publishers.put("INSTAGRAM",
                    new com.videogenerator.publish.MetaReelsPublisher("INSTAGRAM", meta, metaLang)
                            .withClientResolver(resolver));
            publishers.put("FACEBOOK",
                    new com.videogenerator.publish.MetaReelsPublisher("FACEBOOK", meta, metaLang)
                            .withClientResolver(resolver));
        }
        var tiktok = buildTikTokClient(config);
        if (tiktok != null) {
            publishers.put("TIKTOK", new com.videogenerator.publish.TikTokPublisher(
                    tiktok,
                    config.get("tiktok.publish.lang", "en"),
                    config.get("tiktok.privacy.level", "SELF_ONLY"),
                    config.get("tiktok.profile.url",
                            "https://www.tiktok.com/@unsolvedfiles007")));
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

            var pinterestExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();
            var pinterestClient = buildPinterestClient(config);
            if (pinterestClient != null) {
                var pinterestService = new com.videogenerator.pinterest.PinterestPublishService(
                        pinterestClient, config.get("pinterest.board.name", "Small Space Living"),
                        java.nio.file.Path.of(config.get("pinterest.output.dir", "output/pinterest")));
                com.videogenerator.web.BackofficeServer.PinterestBatchLauncher pinterestGenerator =
                        (niche, count) -> pinterestExecutor.submit(() -> {
                            try {
                                var gen = new com.videogenerator.pinterest.PinterestBatchGenerator(
                                        new com.videogenerator.api.OpenAiGptClient(),
                                        new com.videogenerator.api.ImageApiClient());
                                var outDir = java.nio.file.Path.of(
                                        config.get("pinterest.output.dir", "output/pinterest"),
                                        "batch-" + System.currentTimeMillis());
                                gen.generateBatch(niche, count, outDir);
                            } catch (Exception e) {
                                logger.error("Pinterest batch generation failed", e);
                            }
                        });
                server.withPinterest(pinterestService, pinterestGenerator);
                logger.info("Pinterest backoffice section enabled");
            } else {
                logger.info("Pinterest not configured (pinterest.client.id empty) — section disabled");
            }

            var velzonExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();
            var xClient = buildXApiClient(config);
            if (xClient != null) {
                var velzonService = new com.videogenerator.velzon.VelzonPublishService(
                        xClient, java.nio.file.Path.of(
                                config.get("velzon.output.dir", "output/velzon")));
                com.videogenerator.web.BackofficeServer.VelzonBatchLauncher velzonGenerator =
                        (topic, count) -> velzonExecutor.submit(() -> {
                            try {
                                var gen = new com.videogenerator.velzon.VelzonTweetGenerator(
                                        new com.videogenerator.api.OpenAiGptClient());
                                var outDir = java.nio.file.Path.of(
                                        config.get("velzon.output.dir", "output/velzon"),
                                        "batch-" + System.currentTimeMillis());
                                gen.generateBatch(topic, count, outDir);
                            } catch (Exception e) {
                                logger.error("Velzon tweet batch generation failed", e);
                            }
                        });
                server.withVelzon(velzonService, velzonGenerator);
                logger.info("Velzon backoffice section enabled");
            } else {
                logger.info("Velzon not configured (x.client.id empty) — section disabled");
            }

            int port = server.start();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                server.stop();
                pipelineExecutor.shutdownNow();
                publishExecutor.shutdownNow();
                pinterestExecutor.shutdownNow();
                velzonExecutor.shutdownNow();
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
        System.out.println("  publish-meta <jobId> [lang] - Backfill IG/FB for an already-published job");
        System.out.println("  tiktok-auth          - One-time TikTok OAuth authorization");
        System.out.println("  pinterest-auth       - One-time Pinterest OAuth authorization");
        System.out.println("  velzon-x-auth        - One-time X (Twitter) OAuth authorization (PKCE)");
        System.out.println("  pinterest-batch <count> <niche prompt...> - Generate Pinterest pin images+copy (also available in backoffice)");
        System.out.println("  help                 - Show this help");
    }
}
