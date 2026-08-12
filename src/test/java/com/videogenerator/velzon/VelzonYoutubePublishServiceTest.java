package com.videogenerator.velzon;

import com.videogenerator.model.UploadResult;
import com.videogenerator.model.VideoMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class VelzonYoutubePublishServiceTest {

    private Path writeBatch(Path root, String batchId) throws Exception {
        Path dir = root.resolve(batchId);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("manifest.json"), """
            [
              {"narration":"N1","title":"T1","description":"D1",
               "hashtags":["#efatura","#kobi"],"imagePrompt":"P1",
               "imageFile":"video-01.png","published":false},
              {"narration":"N2","title":"T2","description":"D2",
               "hashtags":["#fintech"],"imagePrompt":"P2",
               "imageFile":"video-02.png","published":true,
               "url":"https://www.youtube.com/shorts/OLD"}
            ]""");
        return dir;
    }

    /** Gerçek ffmpeg/ElevenLabs/görsel üretimi olmadan hızlı test için sahte builder. */
    static class FakeVideoBuilder implements VelzonYoutubePublishService.VideoBuilder {
        int buildCalls = 0;
        int cleanupCalls = 0;
        boolean cleanupThrows = false;

        @Override
        public File build(Path batchDir, VelzonYoutubePublishService.ScriptEntry entry, int index)
                throws Exception {
            buildCalls++;
            Path video = batchDir.resolve("video-fake-" + index + ".mp4");
            Files.writeString(video, "fake-mp4-bytes");
            return video.toFile();
        }

        @Override
        public void cleanup(Path batchDir, VelzonYoutubePublishService.ScriptEntry entry, int index)
                throws Exception {
            cleanupCalls++;
            if (cleanupThrows) {
                throw new IllegalStateException("cleanup boom");
            }
        }
    }

    /** Gerçek YouTube API'sine dokunmadan yükleme akışını test eden sahte uploader. */
    static class FakeUploader implements VelzonYoutubePublishService.Uploader {
        AtomicInteger calls = new AtomicInteger();
        File lastFile;
        VideoMetadata lastMetadata;

        @Override
        public UploadResult upload(File videoFile, VideoMetadata metadata) throws Exception {
            calls.incrementAndGet();
            lastFile = videoFile;
            lastMetadata = metadata;
            return new UploadResult("VID" + calls.get(), metadata.getTitle());
        }
    }

    @Test
    void listsBatchesFromDirectory(@TempDir Path root) throws Exception {
        writeBatch(root, "batch-1");
        writeBatch(root, "batch-2");
        var service = new VelzonYoutubePublishService(new FakeUploader(), new FakeVideoBuilder(), root);

        List<VelzonYoutubePublishService.Batch> batches = service.listBatches();

        assertEquals(2, batches.size());
        assertEquals(2, batches.get(0).scripts().size());
    }

    @Test
    void publishesUnpublishedVideoBuildsAndUploadsAndPersists(@TempDir Path root) throws Exception {
        writeBatch(root, "batch-1");
        FakeUploader uploader = new FakeUploader();
        FakeVideoBuilder builder = new FakeVideoBuilder();
        var service = new VelzonYoutubePublishService(uploader, builder, root);

        var updated = service.publishVideo("batch-1", 0);

        assertTrue(updated.published());
        assertEquals("https://www.youtube.com/shorts/VID1", updated.url());
        assertEquals(1, builder.buildCalls);
        assertEquals(1, uploader.calls.get());
        assertEquals(1, builder.cleanupCalls);
        assertEquals("T1", uploader.lastMetadata.getTitle());
        assertEquals("D1", uploader.lastMetadata.getDescription());
        assertEquals(List.of("#efatura", "#kobi"), uploader.lastMetadata.getHashtags());

        // Kalıcılık: manifest.json'a yazılmış olmalı
        var reloaded = service.listBatches().get(0);
        assertTrue(reloaded.scripts().get(0).published());
        assertEquals("https://www.youtube.com/shorts/VID1", reloaded.scripts().get(0).url());
    }

    @Test
    void republishingAlreadyPublishedVideoIsNoOp(@TempDir Path root) throws Exception {
        writeBatch(root, "batch-1");
        FakeUploader uploader = new FakeUploader();
        FakeVideoBuilder builder = new FakeVideoBuilder();
        var service = new VelzonYoutubePublishService(uploader, builder, root);

        var result = service.publishVideo("batch-1", 1); // zaten published:true

        assertEquals(0, builder.buildCalls, "zaten yayınlanmış video için build tetiklenmemeli");
        assertEquals(0, uploader.calls.get(), "zaten yayınlanmış video tekrar API'ye gitmemeli");
        assertEquals("https://www.youtube.com/shorts/OLD", result.url());
    }

    /**
     * VelzonInstagramPublishService'teki emsal bug fix'in aynısı: YouTube
     * upload'ı (geri alınamaz adım) başarılı olduktan SONRA çalışan bir adım
     * (burada: temizlik/cleanup) patlarsa bile published=true zaten diske
     * yazılmış olmalı — aksi halde kullanıcı tekrar "Yayınla" derse aynı video
     * ikinci kez yüklenir (duplicate upload).
     */
    @Test
    void cleanupFailureAfterUploadStillPersistsPublishedTrueToPreventDuplicateUpload(
            @TempDir Path root) throws Exception {
        writeBatch(root, "batch-1");
        FakeUploader uploader = new FakeUploader();
        FakeVideoBuilder builder = new FakeVideoBuilder();
        builder.cleanupThrows = true;
        var service = new VelzonYoutubePublishService(uploader, builder, root);

        var result = service.publishVideo("batch-1", 0);

        // Upload zaten gerçekleşti — published=true ve url kalıcı olmalı,
        // cleanup'ın patlaması bunu etkilememeli.
        assertTrue(result.published());
        assertEquals("https://www.youtube.com/shorts/VID1", result.url());
        assertEquals(1, uploader.calls.get());

        var reloaded = service.listBatches().get(0);
        assertTrue(reloaded.scripts().get(0).published());
        assertEquals("https://www.youtube.com/shorts/VID1", reloaded.scripts().get(0).url());

        // Tekrar çağrılırsa idempotent olmalı — bir daha upload'a gitmemeli
        service.publishVideo("batch-1", 0);
        assertEquals(1, uploader.calls.get(), "zaten yayınlanmış video tekrar yüklenmemeli");
        assertEquals(1, builder.buildCalls, "zaten yayınlanmış video için tekrar build tetiklenmemeli");
    }

    @Test
    void rejectsPathTraversalInBatchId(@TempDir Path root) throws Exception {
        writeBatch(root, "batch-1");
        var service = new VelzonYoutubePublishService(new FakeUploader(), new FakeVideoBuilder(), root);

        assertThrows(IllegalArgumentException.class,
                () -> service.publishVideo("../escape", 0));
    }

    @Test
    void rejectsUnknownBatch(@TempDir Path root) throws Exception {
        var service = new VelzonYoutubePublishService(new FakeUploader(), new FakeVideoBuilder(), root);

        assertThrows(IllegalArgumentException.class,
                () -> service.publishVideo("nonexistent", 0));
    }

    @Test
    void rejectsIndexOutOfRange(@TempDir Path root) throws Exception {
        writeBatch(root, "batch-1");
        var service = new VelzonYoutubePublishService(new FakeUploader(), new FakeVideoBuilder(), root);

        assertThrows(IllegalArgumentException.class,
                () -> service.publishVideo("batch-1", 99));
    }
}
