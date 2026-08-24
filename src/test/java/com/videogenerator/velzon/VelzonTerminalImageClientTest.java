package com.videogenerator.velzon;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class VelzonTerminalImageClientTest {

    static class FakeHttp implements VelzonTerminalImageClient.Http {
        String lastUrl;
        byte[] response = new byte[]{1, 2, 3, 4};
        boolean fail = false;

        @Override
        public byte[] getBytes(String url) throws Exception {
            if (fail) {
                throw new IllegalStateException("Terminal image HTTP 404: not found");
            }
            lastUrl = url;
            return response;
        }
    }

    @Test
    void fetchBuildsUrlFromSymbolAndBaseUrl(@TempDir Path dir) throws Exception {
        FakeHttp http = new FakeHttp();
        var client = new VelzonTerminalImageClient(http, "https://www.velzon.tr");

        client.fetch("THYAO", dir.resolve("image.png"));

        assertEquals("https://www.velzon.tr/teknik-skorlar/THYAO/og-image.png", http.lastUrl);
    }

    @Test
    void fetchWritesResponseBytesToOutFile(@TempDir Path dir) throws Exception {
        FakeHttp http = new FakeHttp();
        http.response = new byte[]{9, 8, 7, 6, 5};
        var client = new VelzonTerminalImageClient(http, "https://www.velzon.tr");
        Path outFile = dir.resolve("image.png");

        client.fetch("THYAO", outFile);

        assertArrayEquals(new byte[]{9, 8, 7, 6, 5}, Files.readAllBytes(outFile));
    }

    @Test
    void fetchCreatesParentDirectoriesIfMissing(@TempDir Path dir) throws Exception {
        FakeHttp http = new FakeHttp();
        var client = new VelzonTerminalImageClient(http, "https://www.velzon.tr");
        Path outFile = dir.resolve("job-123").resolve("image.png");

        client.fetch("THYAO", outFile);

        assertTrue(Files.exists(outFile));
    }

    @Test
    void fetchUrlEncodesSymbol(@TempDir Path dir) throws Exception {
        FakeHttp http = new FakeHttp();
        var client = new VelzonTerminalImageClient(http, "https://www.velzon.tr");

        client.fetch("BRK.A", dir.resolve("image.png"));

        assertEquals("https://www.velzon.tr/teknik-skorlar/BRK.A/og-image.png", http.lastUrl);
    }

    @Test
    void fetchPropagatesHttpFailure(@TempDir Path dir) {
        FakeHttp http = new FakeHttp();
        http.fail = true;
        var client = new VelzonTerminalImageClient(http, "https://www.velzon.tr");

        assertThrows(Exception.class, () -> client.fetch("THYAO", dir.resolve("image.png")));
    }

    @Test
    void fetchThrowsOnEmptyResponse(@TempDir Path dir) {
        FakeHttp http = new FakeHttp();
        http.response = new byte[0];
        var client = new VelzonTerminalImageClient(http, "https://www.velzon.tr");

        assertThrows(IllegalStateException.class, () -> client.fetch("THYAO", dir.resolve("image.png")));
    }
}
