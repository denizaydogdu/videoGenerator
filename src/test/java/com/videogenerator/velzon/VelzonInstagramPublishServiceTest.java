package com.videogenerator.velzon;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VelzonInstagramPublishServiceTest {

    /** Sahte istemci — gerçek Instagram API'sine dokunmadan yayın akışını test eder. */
    static class FakeClient extends VelzonInstagramApiClient {
        int containerCalls = 0;
        int publishCalls = 0;
        int permalinkCalls = 0;
        String lastImageUrl;
        String lastCaption;
        String lastCreationId;
        String lastMediaId;

        FakeClient() {
            super(null, "IGUSER1", "TOKEN1");
        }

        @Override
        public String createMediaContainer(String imageUrl, String caption) {
            containerCalls++;
            lastImageUrl = imageUrl;
            lastCaption = caption;
            return "CONTAINER" + containerCalls;
        }

        @Override
        public String publishContainer(String creationId) {
            publishCalls++;
            lastCreationId = creationId;
            return "MEDIA" + publishCalls;
        }

        @Override
        public String getPermalink(String mediaId) {
            permalinkCalls++;
            lastMediaId = mediaId;
            return "https://www.instagram.com/p/PERM" + permalinkCalls + "/";
        }
    }

    private static final String PUBLIC_BASE_URL = "https://shorts.velzon.tr";

    private Path writeBatch(Path root, String batchId) throws Exception {
        Path dir = root.resolve(batchId);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("post-01.png"), "png");
        Files.writeString(dir.resolve("post-02.png"), "png");
        Files.writeString(dir.resolve("manifest.json"), """
            [
              {"file":"post-01.png","caption":"C1 #efatura","published":false},
              {"file":"post-02.png","caption":"C2 #fintech","published":true,
               "url":"https://www.instagram.com/p/OLD/"}
            ]""");
        return dir;
    }

    @Test
    void listsBatchesFromDirectory(@TempDir Path root) throws Exception {
        writeBatch(root, "batch-1");
        writeBatch(root, "batch-2");
        var service = new VelzonInstagramPublishService(new FakeClient(), root, PUBLIC_BASE_URL);

        List<VelzonInstagramPublishService.Batch> batches = service.listBatches();

        assertEquals(2, batches.size());
        assertEquals(2, batches.get(0).posts().size());
    }

    @Test
    void publishesOnlyUnpublishedPostAndPersistsResult(@TempDir Path root) throws Exception {
        writeBatch(root, "batch-1");
        FakeClient client = new FakeClient();
        var service = new VelzonInstagramPublishService(client, root, PUBLIC_BASE_URL);

        var updated = service.publishPost("batch-1", 0);

        assertTrue(updated.published());
        assertEquals("https://www.instagram.com/p/PERM1/", updated.url());
        assertEquals(1, client.containerCalls);
        assertEquals(1, client.publishCalls);
        assertEquals(1, client.permalinkCalls);
        assertEquals("https://shorts.velzon.tr/api/velzon-instagram/batches/batch-1/images/post-01.png",
                client.lastImageUrl);
        assertEquals("C1 #efatura", client.lastCaption);
        assertEquals("CONTAINER1", client.lastCreationId);
        assertEquals("MEDIA1", client.lastMediaId);

        // Kalıcılık: manifest.json'a yazılmış olmalı
        var reloaded = service.listBatches().get(0);
        assertTrue(reloaded.posts().get(0).published());
        assertEquals("https://www.instagram.com/p/PERM1/", reloaded.posts().get(0).url());
    }

    @Test
    void permalinkFetchFailureStillPersistsPublishedTrueToPreventDuplicatePost(
            @TempDir Path root) throws Exception {
        writeBatch(root, "batch-1");
        FakeClient client = new FakeClient() {
            @Override
            public String getPermalink(String mediaId) {
                permalinkCalls++;
                throw new IllegalStateException("transient network error");
            }
        };
        var service = new VelzonInstagramPublishService(client, root, PUBLIC_BASE_URL);

        var result = service.publishPost("batch-1", 0);

        // Post gerçek hesapta canlı — permalink alınamasa da published=true kalıcı olmalı,
        // aksi halde kullanıcı tekrar "Yayınla" derse ikinci kez API'ye gider (duplicate post).
        assertTrue(result.published());
        assertNull(result.url());
        assertEquals(1, client.containerCalls);
        assertEquals(1, client.publishCalls);

        var reloaded = service.listBatches().get(0);
        assertTrue(reloaded.posts().get(0).published());
        assertNull(reloaded.posts().get(0).url());

        // Tekrar çağrılırsa idempotent olmalı — bir daha API'ye gitmemeli
        service.publishPost("batch-1", 0);
        assertEquals(1, client.containerCalls, "zaten yayınlanmış (url=null olsa da) tekrar gitmemeli");
        assertEquals(1, client.publishCalls);
    }

    @Test
    void republishingAlreadyPublishedPostIsNoOp(@TempDir Path root) throws Exception {
        writeBatch(root, "batch-1");
        FakeClient client = new FakeClient();
        var service = new VelzonInstagramPublishService(client, root, PUBLIC_BASE_URL);

        var result = service.publishPost("batch-1", 1); // zaten published:true

        assertEquals(0, client.containerCalls, "zaten yayınlanmış gönderi tekrar API'ye gitmemeli");
        assertEquals(0, client.publishCalls);
        assertEquals(0, client.permalinkCalls);
        assertEquals("https://www.instagram.com/p/OLD/", result.url());
    }

    @Test
    void rejectsPathTraversalInBatchId(@TempDir Path root) throws Exception {
        writeBatch(root, "batch-1");
        var service = new VelzonInstagramPublishService(new FakeClient(), root, PUBLIC_BASE_URL);

        assertThrows(IllegalArgumentException.class,
                () -> service.publishPost("../escape", 0));
    }

    @Test
    void rejectsUnknownBatch(@TempDir Path root) throws Exception {
        var service = new VelzonInstagramPublishService(new FakeClient(), root, PUBLIC_BASE_URL);

        assertThrows(IllegalArgumentException.class,
                () -> service.publishPost("nonexistent", 0));
    }

    @Test
    void rejectsIndexOutOfRange(@TempDir Path root) throws Exception {
        writeBatch(root, "batch-1");
        var service = new VelzonInstagramPublishService(new FakeClient(), root, PUBLIC_BASE_URL);

        assertThrows(IllegalArgumentException.class,
                () -> service.publishPost("batch-1", 99));
    }

    // ---------- imageFile: path traversal guard ----------

    @Test
    void imageFileReturnsPathForValidInput(@TempDir Path root) throws Exception {
        writeBatch(root, "batch-1");
        var service = new VelzonInstagramPublishService(new FakeClient(), root, PUBLIC_BASE_URL);

        Path file = service.imageFile("batch-1", "post-01.png");

        assertEquals(root.resolve("batch-1").resolve("post-01.png"), file);
    }

    @Test
    void imageFileRejectsSlashInFile(@TempDir Path root) throws Exception {
        writeBatch(root, "batch-1");
        var service = new VelzonInstagramPublishService(new FakeClient(), root, PUBLIC_BASE_URL);

        assertThrows(IllegalArgumentException.class,
                () -> service.imageFile("batch-1", "../secret.png"));
        assertThrows(IllegalArgumentException.class,
                () -> service.imageFile("batch-1", "sub/dir.png"));
        assertThrows(IllegalArgumentException.class,
                () -> service.imageFile("batch-1", "sub\\dir.png"));
    }

    @Test
    void imageFileRejectsPathTraversalInBatchId(@TempDir Path root) throws Exception {
        writeBatch(root, "batch-1");
        var service = new VelzonInstagramPublishService(new FakeClient(), root, PUBLIC_BASE_URL);

        assertThrows(IllegalArgumentException.class,
                () -> service.imageFile("../escape", "post-01.png"));
    }
}
