package com.videogenerator.pinterest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PinterestPublishServiceTest {

    /** Sahte istemci — gerçek Pinterest API'sine dokunmadan yayın akışını test eder. */
    static class FakeClient extends PinterestApiClient {
        int createCalls = 0;
        String lastBoardId;

        FakeClient() {
            super(null, "cid", "csecret", "https://cb/", Path.of("/dev/null"), 0);
        }

        @Override
        public String findBoardIdByName(String boardName) {
            return "BOARD1";
        }

        @Override
        public String createPin(Path imageFile, String boardId, String title,
                                String description, String altText) {
            createCalls++;
            lastBoardId = boardId;
            return "https://www.pinterest.com/pin/PIN" + createCalls + "/";
        }
    }

    private Path writeBatch(Path root, String batchId) throws Exception {
        Path dir = root.resolve(batchId);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("pin-01.png"), "png");
        Files.writeString(dir.resolve("pin-02.png"), "png");
        Files.writeString(dir.resolve("manifest.json"), """
            [
              {"file":"pin-01.png","title":"T1","description":"D1","altText":"A1","published":false},
              {"file":"pin-02.png","title":"T2","description":"D2","altText":"A2","published":true,
               "url":"https://www.pinterest.com/pin/OLD/"}
            ]""");
        return dir;
    }

    @Test
    void listsBatchesFromDirectory(@TempDir Path root) throws Exception {
        writeBatch(root, "batch-1");
        writeBatch(root, "batch-2");
        var service = new PinterestPublishService(new FakeClient(), "Small Space Living", root);

        List<PinterestPublishService.Batch> batches = service.listBatches();

        assertEquals(2, batches.size());
        assertEquals(2, batches.get(0).pins().size());
    }

    @Test
    void publishesOnlyUnpublishedPinAndPersistsResult(@TempDir Path root) throws Exception {
        writeBatch(root, "batch-1");
        FakeClient client = new FakeClient();
        var service = new PinterestPublishService(client, "Small Space Living", root);

        var updated = service.publishPin("batch-1", 0);

        assertTrue(updated.published());
        assertEquals("https://www.pinterest.com/pin/PIN1/", updated.url());
        assertEquals(1, client.createCalls);
        assertEquals("BOARD1", client.lastBoardId);

        // Kalıcılık: manifest.json'a yazılmış olmalı
        var reloaded = service.listBatches().get(0);
        assertTrue(reloaded.pins().get(0).published());
        assertEquals("https://www.pinterest.com/pin/PIN1/", reloaded.pins().get(0).url());
    }

    @Test
    void republishingAlreadyPublishedPinIsNoOp(@TempDir Path root) throws Exception {
        writeBatch(root, "batch-1");
        FakeClient client = new FakeClient();
        var service = new PinterestPublishService(client, "Small Space Living", root);

        var result = service.publishPin("batch-1", 1); // zaten published:true

        assertEquals(0, client.createCalls, "zaten yayınlanmış pin tekrar API'ye gitmemeli");
        assertEquals("https://www.pinterest.com/pin/OLD/", result.url());
    }

    @Test
    void cachesBoardIdAcrossPublishes(@TempDir Path root) throws Exception {
        Path dir = root.resolve("batch-1");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("pin-01.png"), "png");
        Files.writeString(dir.resolve("pin-02.png"), "png");
        Files.writeString(dir.resolve("manifest.json"), """
            [
              {"file":"pin-01.png","title":"T1","description":"D1","altText":"A1","published":false},
              {"file":"pin-02.png","title":"T2","description":"D2","altText":"A2","published":false}
            ]""");
        final int[] lookups = {0};
        FakeClient client = new FakeClient() {
            @Override
            public String findBoardIdByName(String boardName) {
                lookups[0]++;
                return "BOARD1";
            }
        };
        var service = new PinterestPublishService(client, "Small Space Living", root);

        service.publishPin("batch-1", 0);
        service.publishPin("batch-1", 1);

        assertEquals(1, lookups[0], "board id yalnız bir kez sorgulanmalı");
    }
}
