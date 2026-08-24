package com.videogenerator.velzon;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

/**
 * Velzon AI Brifing içerik işi — Django'nun servis-seviyeli AI Brifing
 * uç noktasından ({@link VelzonBriefingClient}) rastgele bir BIST100
 * hissesi için gerçek analiz metni çekip ({@link VelzonBist100SymbolPool}),
 * üç platform için uyarlayıp ({@link VelzonAiBriefingPostGenerator}),
 * Velzon'un kendi sunucusunda sembol başına hazır üretilen gerçek terminal
 * kartıyla ({@link VelzonTerminalImageClient} — AI ile üretilen soyut bir
 * görsel DEĞİL, 2026-08-24 kararı) X/Instagram/Facebook'a otomatik yayınlar.
 *
 * Kullanıcının açık kararı: bu iş TAMAMEN OTOMATİK (insan onayı yok) —
 * diğer Velzon platformlarındaki "üret → manifest'e yaz → Yayınla'ya
 * bas" akışından bilinçli olarak farklıdır ("full marketing", günde 5
 * kez BIST saatleri içinde).
 *
 * Üç platform birbirinden bağımsız denenir — biri başarısız olursa
 * diğerleri etkilenmez (her biri kendi try/catch'inde, sadece loglanır).
 * Bu iş tekrar denemez/idempotent takip yapmaz: her tetikleme taze bir
 * sembol+içerik üretir, bir turun kaçırılması bir sonraki tetiklemede
 * yeni bir sembolle telafi olur — kalıcı "zaten yayınlandı mı" durumu
 * gerektirmez (PublishService'lerdeki batch/manifest deseninin aksine).
 */
public class VelzonAiBriefingJob implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(VelzonAiBriefingJob.class);
    private static final String TIMEFRAME = "1G";
    private static final long IMAGE_RETENTION_DAYS = 7;

    public interface XPoster {
        String uploadMedia(byte[] imageBytes) throws Exception;
        String postTweetWithMedia(String text, String mediaId) throws Exception;
    }

    public interface InstagramPoster {
        String createMediaContainer(String imageUrl, String caption) throws Exception;
        void waitUntilContainerReady(String creationId) throws Exception;
        String publishContainer(String creationId) throws Exception;
    }

    public interface FacebookPoster {
        String createPost(String imageUrl, String caption) throws Exception;
    }

    private final VelzonBriefingClient briefingClient;
    private final VelzonAiBriefingPostGenerator contentGenerator;
    private final VelzonTerminalImageClient terminalImageClient;
    private final XPoster xPoster;
    private final InstagramPoster instagramPoster;
    private final FacebookPoster facebookPoster;
    private final Path outputDir;
    private final String publicBaseUrl;
    private final Supplier<Boolean> tradingTimeCheck;

    private VelzonAiBriefingJob(Builder b) {
        this.briefingClient = b.briefingClient;
        this.contentGenerator = b.contentGenerator;
        this.terminalImageClient = b.terminalImageClient;
        this.xPoster = b.xPoster;
        this.instagramPoster = b.instagramPoster;
        this.facebookPoster = b.facebookPoster;
        this.outputDir = b.outputDir;
        this.publicBaseUrl = b.publicBaseUrl;
        this.tradingTimeCheck = b.tradingTimeCheck;
    }

    @Override
    public void run() {
        if (!tradingTimeCheck.get()) {
            logger.info("BIST kapalı (hafta sonu/tatil/seans dışı) — Velzon AI brifing işi atlanıyor");
            return;
        }
        try {
            runOnce();
        } catch (Exception e) {
            logger.error("Velzon AI brifing işi başarısız", e);
        }
    }

    /**
     * {@link #run()} gibi ama BIST-açık kontrolünü ({@code tradingTimeCheck})
     * ATLAR — CLI'daki {@code velzon-ai-briefing-test} komutu için, gözetimli
     * manuel doğrulama amaçlı. Dry-run DEĞİLDİR: gerçek X/Instagram/Facebook
     * hesaplarına gerçek bir post gönderir.
     */
    public void runOnceForTesting() throws Exception {
        runOnce();
    }

    /** Tek bir çalıştırmanın tam akışı — sembol seç, brifing çek, uyarla, görsel üret, 3 platforma postla. */
    void runOnce() throws Exception {
        cleanupOldImages();

        String symbol = VelzonBist100SymbolPool.pickRandom(1).get(0);
        logger.info("Velzon AI brifing işi başlıyor: {}", symbol);

        VelzonBriefingClient.Briefing briefing = briefingClient.fetchBriefing(symbol, TIMEFRAME);
        VelzonAiBriefingPostGenerator.AdaptedContent adapted = contentGenerator.adapt(briefing);

        String jobId = "job-" + System.currentTimeMillis();
        Path imgPath = outputDir.resolve(jobId).resolve("image.png");
        terminalImageClient.fetch(symbol, imgPath);
        String imageUrl = publicBaseUrl + "/api/velzon-ai-briefing/images/" + jobId + "/image.png";

        publishToX(symbol, adapted, imgPath);
        publishToInstagram(symbol, adapted, imageUrl);
        publishToFacebook(symbol, adapted, imageUrl);
    }

    /**
     * Günde 5x, süresiz çalıştığı için outputDir'de biriken eski job-*
     * görsel dizinlerini temizler ({@link #IMAGE_RETENTION_DAYS} gün üstü).
     * Best-effort: tarama/silme başarısız olursa loglanır, runOnce'ı
     * bloklamaz (görsel temizliği yayın akışından daha az kritik).
     */
    private void cleanupOldImages() {
        if (!Files.isDirectory(outputDir)) {
            return;
        }
        Instant cutoff = Instant.now().minus(IMAGE_RETENTION_DAYS, ChronoUnit.DAYS);
        try (var entries = Files.list(outputDir)) {
            entries.filter(Files::isDirectory).forEach(dir -> {
                try {
                    if (Files.getLastModifiedTime(dir).toInstant().isBefore(cutoff)) {
                        deleteRecursively(dir);
                    }
                } catch (IOException e) {
                    logger.warn("Velzon AI brifing eski görsel dizini temizlenemedi: {}", dir, e);
                }
            });
        } catch (IOException e) {
            logger.warn("Velzon AI brifing görsel temizlik taraması başarısız", e);
        }
    }

    private static void deleteRecursively(Path dir) throws IOException {
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    logger.warn("Silinemedi: {}", p, e);
                }
            });
        }
    }

    private void publishToX(String symbol, VelzonAiBriefingPostGenerator.AdaptedContent adapted,
                            Path imgPath) {
        try {
            byte[] bytes = Files.readAllBytes(imgPath);
            String mediaId = xPoster.uploadMedia(bytes);
            String url = xPoster.postTweetWithMedia(adapted.xText(), mediaId);
            logger.info("Velzon AI brifing X'e yayınlandı: {} -> {}", symbol, url);
        } catch (Exception e) {
            logger.error("Velzon AI brifing X yayını başarısız: {}", symbol, e);
        }
    }

    private void publishToInstagram(String symbol, VelzonAiBriefingPostGenerator.AdaptedContent adapted,
                                    String imageUrl) {
        try {
            String creationId = instagramPoster.createMediaContainer(imageUrl, adapted.instagramCaption());
            instagramPoster.waitUntilContainerReady(creationId);
            String mediaId = instagramPoster.publishContainer(creationId);
            logger.info("Velzon AI brifing Instagram'a yayınlandı: {} -> {}", symbol, mediaId);
        } catch (Exception e) {
            logger.error("Velzon AI brifing Instagram yayını başarısız: {}", symbol, e);
        }
    }

    private void publishToFacebook(String symbol, VelzonAiBriefingPostGenerator.AdaptedContent adapted,
                                   String imageUrl) {
        try {
            String postId = facebookPoster.createPost(imageUrl, adapted.facebookCaption());
            logger.info("Velzon AI brifing Facebook'a yayınlandı: {} -> {}", symbol, postId);
        } catch (Exception e) {
            logger.error("Velzon AI brifing Facebook yayını başarısız: {}", symbol, e);
        }
    }

    public static class Builder {
        private VelzonBriefingClient briefingClient;
        private VelzonAiBriefingPostGenerator contentGenerator;
        private VelzonTerminalImageClient terminalImageClient;
        private XPoster xPoster;
        private InstagramPoster instagramPoster;
        private FacebookPoster facebookPoster;
        private Path outputDir;
        private String publicBaseUrl;
        private Supplier<Boolean> tradingTimeCheck = BistTradingCalendar::isTradingTimeNow;

        public Builder briefingClient(VelzonBriefingClient v) { this.briefingClient = v; return this; }
        public Builder contentGenerator(VelzonAiBriefingPostGenerator v) { this.contentGenerator = v; return this; }
        public Builder terminalImageClient(VelzonTerminalImageClient v) { this.terminalImageClient = v; return this; }
        public Builder xPoster(XPoster v) { this.xPoster = v; return this; }
        public Builder instagramPoster(InstagramPoster v) { this.instagramPoster = v; return this; }
        public Builder facebookPoster(FacebookPoster v) { this.facebookPoster = v; return this; }
        public Builder outputDir(Path v) { this.outputDir = v; return this; }
        public Builder publicBaseUrl(String v) { this.publicBaseUrl = v; return this; }

        /** Testte BIST-açık kontrolünü sahte bir sonuçla değiştirmek için. */
        public Builder tradingTimeCheck(Supplier<Boolean> v) { this.tradingTimeCheck = v; return this; }

        public VelzonAiBriefingJob build() {
            return new VelzonAiBriefingJob(this);
        }
    }
}
