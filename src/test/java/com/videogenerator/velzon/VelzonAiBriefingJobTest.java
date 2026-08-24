package com.videogenerator.velzon;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class VelzonAiBriefingJobTest {

    static class FakeBriefingClient extends VelzonBriefingClient {
        int calls = 0;
        FakeBriefingClient() { super(null, "https://www.velzon.tr", "key"); }

        @Override
        public Briefing fetchBriefing(String symbol, String timeframe) {
            calls++;
            return new Briefing(symbol, timeframe, "TEKNİK GÖRÜNÜM\nTest.\n\nÖZET\nTest. [SKOR: NOTR]");
        }
    }

    static class FakePostGenerator extends VelzonAiBriefingPostGenerator {
        int calls = 0;
        FakePostGenerator() { super(null); }

        @Override
        public AdaptedContent adapt(VelzonBriefingClient.Briefing briefing) {
            calls++;
            return new AdaptedContent(
                    briefing.symbol() + " kısa özet. https://www.velzon.tr/terminal/",
                    briefing.symbol() + " Instagram özeti.",
                    briefing.symbol() + " Facebook özeti.");
        }
    }

    static class FakeTerminalImageClient extends VelzonTerminalImageClient {
        int calls = 0;
        FakeTerminalImageClient() { super(null, "https://www.velzon.tr"); }

        @Override
        public File fetch(String symbol, Path outFile) throws Exception {
            calls++;
            Files.createDirectories(outFile.getParent());
            Files.write(outFile, "fake-terminal-png-bytes".getBytes());
            return outFile.toFile();
        }
    }

    static class FakeXPoster implements VelzonAiBriefingJob.XPoster {
        int uploadCalls = 0, tweetCalls = 0;
        boolean fail = false;
        @Override
        public String uploadMedia(byte[] bytes) throws Exception {
            if (fail) throw new IllegalStateException("X upload boom");
            uploadCalls++;
            return "MEDIA1";
        }
        @Override
        public String postTweetWithMedia(String text, String mediaId) {
            tweetCalls++;
            return "https://x.com/i/status/1";
        }
    }

    static class FakeIgPoster implements VelzonAiBriefingJob.InstagramPoster {
        int calls = 0;
        boolean fail = false;
        @Override
        public String createMediaContainer(String imageUrl, String caption) throws Exception {
            if (fail) throw new IllegalStateException("IG boom");
            return "CONTAINER1";
        }
        @Override
        public void waitUntilContainerReady(String creationId) { }
        @Override
        public String publishContainer(String creationId) {
            calls++;
            return "MEDIAIG1";
        }
    }

    static class FakeFbPoster implements VelzonAiBriefingJob.FacebookPoster {
        int calls = 0;
        boolean fail = false;
        @Override
        public String createPost(String imageUrl, String caption) throws Exception {
            if (fail) throw new IllegalStateException("FB boom");
            calls++;
            return "POST1";
        }
    }

    private VelzonAiBriefingJob.Builder builder(Path outDir, FakeBriefingClient briefing,
            FakePostGenerator gen, FakeTerminalImageClient img, VelzonAiBriefingJob.XPoster x,
            VelzonAiBriefingJob.InstagramPoster ig, FakeFbPoster fb) {
        return new VelzonAiBriefingJob.Builder()
                .briefingClient(briefing)
                .contentGenerator(gen)
                .terminalImageClient(img)
                .xPoster(x)
                .instagramPoster(ig)
                .facebookPoster(fb)
                .outputDir(outDir)
                .publicBaseUrl("https://shorts.velzon.tr");
    }

    @Test
    void runOncePostsToAllThreePlatforms(@TempDir Path outDir) throws Exception {
        FakeBriefingClient briefing = new FakeBriefingClient();
        FakePostGenerator gen = new FakePostGenerator();
        FakeTerminalImageClient img = new FakeTerminalImageClient();
        FakeXPoster x = new FakeXPoster();
        FakeIgPoster ig = new FakeIgPoster();
        FakeFbPoster fb = new FakeFbPoster();
        VelzonAiBriefingJob job = builder(outDir, briefing, gen, img, x, ig, fb).build();

        job.runOnce();

        assertEquals(1, briefing.calls);
        assertEquals(1, gen.calls);
        assertEquals(1, img.calls);
        assertEquals(1, x.uploadCalls);
        assertEquals(1, x.tweetCalls);
        assertEquals(1, ig.calls);
        assertEquals(1, fb.calls);
    }

    @Test
    void oneFailingPlatformDoesNotBlockTheOthers(@TempDir Path outDir) throws Exception {
        FakeBriefingClient briefing = new FakeBriefingClient();
        FakePostGenerator gen = new FakePostGenerator();
        FakeTerminalImageClient img = new FakeTerminalImageClient();
        FakeXPoster x = new FakeXPoster();
        x.fail = true;
        FakeIgPoster ig = new FakeIgPoster();
        FakeFbPoster fb = new FakeFbPoster();
        VelzonAiBriefingJob job = builder(outDir, briefing, gen, img, x, ig, fb).build();

        assertDoesNotThrow(job::runOnce);

        assertEquals(0, x.uploadCalls);
        assertEquals(1, ig.calls, "X başarısız olsa da Instagram denenmeli");
        assertEquals(1, fb.calls, "X başarısız olsa da Facebook denenmeli");
    }

    @Test
    void imagesAreServedFromOutputDirWithPublicBaseUrl(@TempDir Path outDir) throws Exception {
        FakeBriefingClient briefing = new FakeBriefingClient();
        FakePostGenerator gen = new FakePostGenerator();
        FakeTerminalImageClient img = new FakeTerminalImageClient();
        FakeXPoster x = new FakeXPoster();
        AtomicInteger seenImageUrl = new AtomicInteger();
        VelzonAiBriefingJob.InstagramPoster ig = new VelzonAiBriefingJob.InstagramPoster() {
            @Override
            public String createMediaContainer(String imageUrl, String caption) {
                assertTrue(imageUrl.startsWith("https://shorts.velzon.tr/api/velzon-ai-briefing/images/"));
                assertTrue(imageUrl.endsWith("/image.png"));
                seenImageUrl.incrementAndGet();
                return "CONTAINER1";
            }
            @Override
            public void waitUntilContainerReady(String creationId) { }
            @Override
            public String publishContainer(String creationId) {
                return "MEDIAIG1";
            }
        };
        FakeFbPoster fb = new FakeFbPoster();
        VelzonAiBriefingJob job = builder(outDir, briefing, gen, img, x, ig, fb).build();

        job.runOnce();

        assertEquals(1, seenImageUrl.get());
    }

    @Test
    void runOnceDeletesImageDirsOlderThanRetentionWindow(@TempDir Path outDir) throws Exception {
        Path oldJobDir = outDir.resolve("job-old");
        Files.createDirectories(oldJobDir);
        Files.write(oldJobDir.resolve("image.png"), "old".getBytes());
        Files.setLastModifiedTime(oldJobDir,
                java.nio.file.attribute.FileTime.from(
                        java.time.Instant.now().minus(30, java.time.temporal.ChronoUnit.DAYS)));

        Path recentJobDir = outDir.resolve("job-recent");
        Files.createDirectories(recentJobDir);
        Files.write(recentJobDir.resolve("image.png"), "recent".getBytes());

        FakeBriefingClient briefing = new FakeBriefingClient();
        FakePostGenerator gen = new FakePostGenerator();
        FakeTerminalImageClient img = new FakeTerminalImageClient();
        FakeXPoster x = new FakeXPoster();
        FakeIgPoster ig = new FakeIgPoster();
        FakeFbPoster fb = new FakeFbPoster();
        VelzonAiBriefingJob job = builder(outDir, briefing, gen, img, x, ig, fb).build();

        job.runOnce();

        assertFalse(Files.exists(oldJobDir), "7 günden eski görsel dizini silinmeli");
        assertTrue(Files.exists(recentJobDir), "yeni görsel dizini korunmalı");
    }

    @Test
    void skipsWhenBistIsClosed(@TempDir Path outDir) throws Exception {
        FakeBriefingClient briefing = new FakeBriefingClient();
        FakePostGenerator gen = new FakePostGenerator();
        FakeTerminalImageClient img = new FakeTerminalImageClient();
        FakeXPoster x = new FakeXPoster();
        FakeIgPoster ig = new FakeIgPoster();
        FakeFbPoster fb = new FakeFbPoster();
        VelzonAiBriefingJob job = builder(outDir, briefing, gen, img, x, ig, fb)
                .tradingTimeCheck(() -> false) // BIST kapalı simülasyonu
                .build();

        job.run();

        assertEquals(0, briefing.calls, "BIST kapalıyken hiçbir şey tetiklenmemeli");
    }

    @Test
    void runDoesNotThrowEvenIfRunOnceFailsEntirely(@TempDir Path outDir) throws Exception {
        VelzonAiBriefingJob.Builder b = new VelzonAiBriefingJob.Builder()
                .briefingClient(new FakeBriefingClient() {
                    @Override
                    public Briefing fetchBriefing(String symbol, String timeframe) {
                        throw new RuntimeException("Django boom");
                    }
                })
                .contentGenerator(new FakePostGenerator())
                .terminalImageClient(new FakeTerminalImageClient())
                .xPoster(new FakeXPoster())
                .instagramPoster(new FakeIgPoster())
                .facebookPoster(new FakeFbPoster())
                .outputDir(outDir)
                .publicBaseUrl("https://shorts.velzon.tr");

        assertDoesNotThrow(b.build()::run);
    }
}
