package com.videogenerator.channel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class ChannelStoreTest {
    public static final String VALID = """
        {"channelId":"truecrime-en","displayName":"Unsolved Files",
         "niche":{"topic":"unsolved crime","keywords":["cold case"]},
         "stylePrefix":"1970s film grain, no human faces",
         "voiceId":"v123","languages":["en","es"],"platforms":["YOUTUBE"],
         "targetDurationSeconds":75,"sceneCount":6,
         "youtubeTokenFile":"config/tokens/truecrime-en.json","enabled":true}""";

    @Test
    void loadsValidProfileAndSkipsDisabled(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("truecrime-en.json"), VALID);
        Files.writeString(dir.resolve("off.json"), VALID.replace("\"enabled\":true", "\"enabled\":false")
                                                        .replace("truecrime-en", "off-ch"));
        ChannelStore store = new ChannelStore(dir);
        List<ChannelProfile> list = store.loadEnabled();
        assertEquals(1, list.size());
        assertEquals("truecrime-en", list.get(0).getChannelId());
        assertEquals(6, list.get(0).getSceneCount());
    }

    @Test
    void rejectsPathTraversalAndEmptyFile(@TempDir Path dir) throws Exception {
        ChannelStore store = new ChannelStore(dir);
        assertThrows(IllegalArgumentException.class, () -> store.load("../evil"));
        Files.writeString(dir.resolve("empty.json"), "");
        assertThrows(IllegalArgumentException.class, () -> store.load("empty"));
    }

    @Test
    void validateRejectsMissingVoiceAndBadDuration(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("bad.json"),
            VALID.replace("\"voiceId\":\"v123\",", "").replace("75", "30"));
        ChannelStore store = new ChannelStore(dir);
        assertThrows(IllegalArgumentException.class, () -> store.load("bad"));
    }
}
