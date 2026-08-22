package com.videogenerator.velzon;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VelzonFacebookPublishServiceTest {

    /** Sahte istemci — gerçek Facebook API'sine dokunmadan yayın akışını test eder. */
    static class FakeClient extends VelzonFacebookApiClient {
        int createCalls = 0;
        int permalinkCalls = 0;
        String lastImageUrl;
        String lastCaption;
        String lastPostId;

        FakeClient() {
            super(null, "PAGE1", "TOKEN1");
        }

        @Override
        public String createPost(String imageUrl, String caption) {
            createCalls++;
            lastImageUrl = imageUrl;
            lastCaption = caption;
            return "POST" + createCalls;
        }

        @Override
        public String getPermalink(String postId) {
            permalinkCalls++;
            lastPostId = postId;
            return "https://www.facebook.com/PAGE1/posts/PERM" + permalinkCalls + "/";
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
               "url":"https://www.facebook.com/PAGE1/posts/OLD/"}
            ]""");
        return dir;
    }

    @Test
    void listsBatchesFromDirectory(@TempDir Path root) throws Exception {
        writeBatch(root, "batch-1");
        writeBatch(root, "batch-2");
        var service = new VelzonFacebookPublishService(new FakeClient(), root, PUBLIC_BASE_URL);

        List<VelzonFacebookPublishService.Batch> batches = service.listBatches();

        assertEquals(2, batches.size());
        assertEquals(2, batches.get(0).posts().size());
    }

    @Test
    void publishesOnlyUnpublishedPostAndPersistsResult(@TempDir Path root) throws Exception {
        writeBatch(root, "batch-1");
        FakeClient client = new FakeClient();
        var service = new VelzonFacebookPublishService(client, root, PUBLIC_BASE_URL);

        var updated = service.publishPost("batch-1", 0);

        assertTrue(updated.published());
        assertEquals("https://www.facebook.com/PAGE1/posts/PERM1/", updated.url());
        assertEquals(1, client.createCalls);
        assertEquals(1, client.permalinkCalls);
        assertEquals("https://shorts.velzon.tr/api/velzon-facebook/batches/batch-1/images/post-01.png",
                client.lastImageUrl);
        assertEquals("C1 #efatura", client.lastCaption);
        assertEquals("POST1", client.lastPostId);

        // Kalıcılık: manifest.json'a yazılmış olmalı
        var reloaded = service.listBatches().get(0);
        assertTrue(reloaded.posts().get(0).published());
        assertEquals("https://www.facebook.com/PAGE1/posts/PERM1/", reloaded.posts().get(0).url());
    }

    @Test
    void permalinkFetchFailureStillPersistsPublishedTrueToPreventDuplicatePost(
            @TempDir Path root) throws Exception {
        writeBatch(root, "batch-1");
        FakeClient client = new FakeClient() {
            @Override
            public String getPermalink(String postId) {
                permalinkCalls++;
                throw new IllegalStateException("transient network error");
            }
        };
        var service = new VelzonFacebookPublishService(client, root, PUBLIC_BASE_URL);

        var result = service.publishPost("batch-1", 0);

        // Post gerçek hesapta canlı — permalink alınamasa da published=true kalıcı olmalı,
        // aksi halde kullanıcı tekrar "Yayınla" derse ikinci kez API'ye gider (duplicate post).
        assertTrue(result.published());
        assertNull(result.url());
        assertEquals(1, client.createCalls);

        var reloaded = service.listBatches().get(0);
        assertTrue(reloaded.posts().get(0).published());
        assertNull(reloaded.posts().get(0).url());

        // Tekrar çağrılırsa idempotent olmalı — bir daha API'ye gitmemeli
        service.publishPost("batch-1", 0);
        assertEquals(1, client.createCalls, "zaten yayınlanmış (url=null olsa da) tekrar gitmemeli");
    }

    @Test
    void republishingAlreadyPublishedPostIsNoOp(@TempDir Path root) throws Exception {
        writeBatch(root, "batch-1");
        FakeClient client = new FakeClient();
        var service = new VelzonFacebookPublishService(client, root, PUBLIC_BASE_URL);

        var result = service.publishPost("batch-1", 1); // zaten published:true

        assertEquals(0, client.createCalls, "zaten yayınlanmış gönderi tekrar API'ye gitmemeli");
        assertEquals(0, client.permalinkCalls);
        assertEquals("https://www.facebook.com/PAGE1/posts/OLD/", result.url());
    }

    @Test
    void rejectsPathTraversalInBatchId(@TempDir Path root) throws Exception {
        writeBatch(root, "batch-1");
        var service = new VelzonFacebookPublishService(new FakeClient(), root, PUBLIC_BASE_URL);

        assertThrows(IllegalArgumentException.class,
                () -> service.publishPost("../escape", 0));
    }

    @Test
    void rejectsUnknownBatch(@TempDir Path root) throws Exception {
        var service = new VelzonFacebookPublishService(new FakeClient(), root, PUBLIC_BASE_URL);

        assertThrows(IllegalArgumentException.class,
                () -> service.publishPost("nonexistent", 0));
    }

    @Test
    void rejectsIndexOutOfRange(@TempDir Path root) throws Exception {
        writeBatch(root, "batch-1");
        var service = new VelzonFacebookPublishService(new FakeClient(), root, PUBLIC_BASE_URL);

        assertThrows(IllegalArgumentException.class,
                () -> service.publishPost("batch-1", 99));
    }

    // ---------- imageFile: path traversal guard ----------

    @Test
    void imageFileReturnsPathForValidInput(@TempDir Path root) throws Exception {
        writeBatch(root, "batch-1");
        var service = new VelzonFacebookPublishService(new FakeClient(), root, PUBLIC_BASE_URL);

        Path file = service.imageFile("batch-1", "post-01.png");

        assertEquals(root.resolve("batch-1").resolve("post-01.png"), file);
    }

    @Test
    void imageFileRejectsSlashInFile(@TempDir Path root) throws Exception {
        writeBatch(root, "batch-1");
        var service = new VelzonFacebookPublishService(new FakeClient(), root, PUBLIC_BASE_URL);

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
        var service = new VelzonFacebookPublishService(new FakeClient(), root, PUBLIC_BASE_URL);

        assertThrows(IllegalArgumentException.class,
                () -> service.imageFile("../escape", "post-01.png"));
    }
}
