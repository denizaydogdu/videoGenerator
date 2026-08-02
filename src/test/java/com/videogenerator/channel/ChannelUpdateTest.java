package com.videogenerator.channel;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ChannelUpdateTest {

    private ChannelStore storeWith(Path root) throws Exception {
        Path dir = root.resolve("channels");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("ch1.json"),
                ChannelStoreTest.VALID.replace("truecrime-en", "ch1"));
        return new ChannelStore(dir);
    }

    @Test
    void updatesWhitelistedFieldsAndPersists(@TempDir Path root) throws Exception {
        ChannelStore store = storeWith(root);
        JsonObject patch = new Gson().fromJson("""
            {"displayName":"Yeni Ad","sceneCount":4,
             "meta":{"publishLang":"tr"},
             "channelId":"HACKED"}""", JsonObject.class);

        ChannelProfile updated = store.update("ch1", patch);

        assertEquals("Yeni Ad", updated.getDisplayName());
        assertEquals(4, updated.getSceneCount());
        assertEquals("tr", updated.getMeta().getPublishLang());
        assertEquals("ch1", updated.getChannelId(), "channelId patch'lenemez");
        // kalıcılık
        ChannelProfile reloaded = store.load("ch1");
        assertEquals("Yeni Ad", reloaded.getDisplayName());
        assertEquals("tr", reloaded.getMeta().getPublishLang());
    }

    @Test
    void invalidPatchRejectedAndFileUntouched(@TempDir Path root) throws Exception {
        ChannelStore store = storeWith(root);
        JsonObject patch = new Gson().fromJson("{\"sceneCount\":99}", JsonObject.class);

        assertThrows(IllegalArgumentException.class, () -> store.update("ch1", patch));
        assertNotEquals(99, store.load("ch1").getSceneCount(), "geçersiz patch yazılmamalı");
    }
}
