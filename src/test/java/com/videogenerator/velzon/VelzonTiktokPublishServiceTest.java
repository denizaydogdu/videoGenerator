package com.videogenerator.velzon;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class VelzonTiktokPublishServiceTest {

    private Path writeBatch(Path root, String batchId, boolean videoRendered) throws Exception {
        Path dir = root.resolve(batchId);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("manifest.json"), """
            [
              {"narration":"N1","title":"T1","description":"D1",
               "hashtags":["#borsa","#bist100"],"imagePrompt":"P1",
               "imageFile":"video-01.png","published":true,
               "url":"https://www.youtube.com/shorts/YT1"},
              {"narration":"N2","title":"T2","description":"D2",
               "hashtags":["#hisse"],"imagePrompt":"P2",
               "imageFile":"video-02.png","published":false}
            ]""");
        if (videoRendered) {
            Files.writeString(dir.resolve("video-01.mp4"), "fake-mp4-bytes");
        }
        return dir;
    }

    static class FakePoster implements VelzonTiktokPublishService.Poster {
        AtomicInteger calls = new AtomicInteger();
        Path lastVideo;
        String lastTitle;
        String lastPrivacyLevel;
        boolean throwsError = false;

        @Override
        public String post(Path video, String title, String privacyLevel) throws Exception {
            if (throwsError) {
                throw new IllegalStateException("tiktok boom");
            }
            calls.incrementAndGet();
            lastVideo = video;
            lastTitle = title;
            lastPrivacyLevel = privacyLevel;
            return "publish_id_" + calls.get();
        }
    }

    @Test
    void listsBatchesWithVideoReadyFlag(@TempDir Path root) throws Exception {
        writeBatch(root, "batch-1", true);
        var service = new VelzonTiktokPublishService(new FakePoster(), root, "SELF_ONLY");

        List<VelzonTiktokPublishService.Batch> batches = service.listBatches();

        assertEquals(1, batches.size());
        var scripts = batches.get(0).scripts();
        assertEquals(2, scripts.size());
        assertTrue(scripts.get(0).videoReady(), "video-01.mp4 diskte var");
        assertFalse(scripts.get(1).videoReady(), "video-02.mp4 hiç render edilmedi");
        assertFalse(scripts.get(0).tiktokPublished());
        assertEquals(List.of("#borsa", "#bist100"), scripts.get(0).hashtags());
    }

    @Test
    void publishesReadyVideoAndPersists(@TempDir Path root) throws Exception {
        writeBatch(root, "batch-1", true);
        FakePoster poster = new FakePoster();
        var service = new VelzonTiktokPublishService(poster, root, "SELF_ONLY");

        var result = service.publishToTiktok("batch-1", 0);

        assertTrue(result.tiktokPublished());
        assertEquals("publish_id_1", result.tiktokPublishId());
        assertEquals(1, poster.calls.get());
        assertEquals("T1", poster.lastTitle);
        assertEquals("SELF_ONLY", poster.lastPrivacyLevel);
        assertEquals(root.resolve("batch-1").resolve("video-01.mp4"), poster.lastVideo);

        // Kalıcılık: tekrar listelendiğinde de published görünmeli
        var reloaded = service.listBatches().get(0).scripts().get(0);
        assertTrue(reloaded.tiktokPublished());
        assertEquals("publish_id_1", reloaded.tiktokPublishId());
    }

    @Test
    void rejectsPublishWhenVideoNotYetRendered(@TempDir Path root) throws Exception {
        writeBatch(root, "batch-1", true); // sadece video-01.mp4 var, video-02.mp4 yok
        var service = new VelzonTiktokPublishService(new FakePoster(), root, "SELF_ONLY");

        assertThrows(IllegalStateException.class,
                () -> service.publishToTiktok("batch-1", 1));
    }

    @Test
    void republishingAlreadyPublishedEntryIsNoOp(@TempDir Path root) throws Exception {
        writeBatch(root, "batch-1", true);
        FakePoster poster = new FakePoster();
        var service = new VelzonTiktokPublishService(poster, root, "SELF_ONLY");
        service.publishToTiktok("batch-1", 0);

        var result = service.publishToTiktok("batch-1", 0);

        assertEquals(1, poster.calls.get(), "zaten yayınlanmış içerik tekrar TikTok'a gönderilmemeli");
        assertEquals("publish_id_1", result.tiktokPublishId());
    }

    @Test
    void rejectsPathTraversalInBatchId(@TempDir Path root) throws Exception {
        writeBatch(root, "batch-1", true);
        var service = new VelzonTiktokPublishService(new FakePoster(), root, "SELF_ONLY");

        assertThrows(IllegalArgumentException.class,
                () -> service.publishToTiktok("../escape", 0));
    }

    @Test
    void rejectsUnknownBatch(@TempDir Path root) throws Exception {
        var service = new VelzonTiktokPublishService(new FakePoster(), root, "SELF_ONLY");

        assertThrows(IllegalArgumentException.class,
                () -> service.publishToTiktok("nonexistent", 0));
    }

    @Test
    void rejectsIndexOutOfRange(@TempDir Path root) throws Exception {
        writeBatch(root, "batch-1", true);
        var service = new VelzonTiktokPublishService(new FakePoster(), root, "SELF_ONLY");

        assertThrows(IllegalArgumentException.class,
                () -> service.publishToTiktok("batch-1", 99));
    }
}
