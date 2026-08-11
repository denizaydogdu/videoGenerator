package com.videogenerator.velzon;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VelzonPublishServiceTest {

    static class FakeClient extends XApiClient {
        int postCalls = 0;

        FakeClient() {
            super(null, "cid", "csecret", "https://cb/", Path.of("/dev/null"));
        }

        @Override
        public String postTweet(String text) {
            postCalls++;
            return "https://x.com/i/status/TWEET" + postCalls;
        }
    }

    private Path writeBatch(Path root, String batchId) throws Exception {
        Path dir = root.resolve(batchId);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("manifest.json"), """
            [
              {"topic":"T1","text":"tweet 1","published":false},
              {"topic":"T2","text":"tweet 2","published":true,
               "url":"https://x.com/i/status/OLD"}
            ]""");
        return dir;
    }

    @Test
    void listsBatchesFromDirectory(@TempDir Path root) throws Exception {
        writeBatch(root, "batch-1");
        var service = new VelzonPublishService(new FakeClient(), root);

        List<VelzonPublishService.Batch> batches = service.listBatches();

        assertEquals(1, batches.size());
        assertEquals(2, batches.get(0).tweets().size());
    }

    @Test
    void publishesOnlyUnpublishedTweetAndPersistsResult(@TempDir Path root) throws Exception {
        writeBatch(root, "batch-1");
        FakeClient client = new FakeClient();
        var service = new VelzonPublishService(client, root);

        var updated = service.publishTweet("batch-1", 0);

        assertTrue(updated.published());
        assertEquals("https://x.com/i/status/TWEET1", updated.url());
        assertEquals(1, client.postCalls);

        var reloaded = service.listBatches().get(0);
        assertTrue(reloaded.tweets().get(0).published());
    }

    @Test
    void republishingAlreadyPublishedTweetIsNoOp(@TempDir Path root) throws Exception {
        writeBatch(root, "batch-1");
        FakeClient client = new FakeClient();
        var service = new VelzonPublishService(client, root);

        var result = service.publishTweet("batch-1", 1); // zaten published:true

        assertEquals(0, client.postCalls);
        assertEquals("https://x.com/i/status/OLD", result.url());
    }

    @Test
    void rejectsPathTraversalInBatchId(@TempDir Path root) throws Exception {
        writeBatch(root, "batch-1");
        var service = new VelzonPublishService(new FakeClient(), root);

        assertThrows(IllegalArgumentException.class,
                () -> service.publishTweet("../escape", 0));
    }
}
