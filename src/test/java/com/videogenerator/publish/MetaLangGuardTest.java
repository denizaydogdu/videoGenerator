package com.videogenerator.publish;

import com.videogenerator.job.Job;
import com.videogenerator.model.LangVariant;
import com.videogenerator.model.Publication;
import com.videogenerator.model.VideoMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IG/FB duplicate-content koruması: aynı görsellerin 3 dil kopyası tek
 * hesaba basılırsa Meta erişim cezası kesebiliyor (2026 orijinallik
 * kuralları). meta.publish.lang ile sınırlanınca diğer diller SKIPPED_LANG
 * olarak kaydedilir — yayın idempotent kalır, tekrar denenmez.
 */
class MetaLangGuardTest {

    private LangVariant variant(String lang) {
        LangVariant v = new LangVariant();
        v.setLang(lang);
        VideoMetadata md = new VideoMetadata();
        md.setTitle("T");
        md.setDescription("D");
        md.setHashtags(List.of("#x"));
        v.setMetadata(md);
        v.setRenderFile("renders/" + lang + ".mp4");
        return v;
    }

    @Test
    void otherLanguagesSkippedWithoutApiCall(@TempDir Path dir) throws Exception {
        // client=null: API'ye dokunulursa NPE ile patlar — dokunulmamalı
        MetaReelsPublisher pub = new MetaReelsPublisher("INSTAGRAM", null, "en");

        Publication result = pub.publish(Job.create("ch1"), variant("tr"), null, dir);

        assertEquals("SKIPPED_LANG", result.getStatus());
        assertEquals("INSTAGRAM", result.getPlatform());
    }

    @Test
    void allowedLanguagePublishes(@TempDir Path dir) throws Exception {
        Files.createDirectories(dir.resolve("renders"));
        Files.writeString(dir.resolve("renders/en.mp4"), "mp4");
        MetaApiClient client = new MetaApiClient(
                new MetaApiClientTest.FakeHttp(), "USERTOK", "PAGE1", "IGUSER", 0);
        MetaReelsPublisher pub = new MetaReelsPublisher("INSTAGRAM", client, "en");

        Publication result = pub.publish(Job.create("ch1"), variant("en"), null, dir);

        assertEquals("PUBLISHED", result.getStatus());
        assertEquals("https://www.instagram.com/reel/ABC/", result.getUrl());
    }

    @Test
    void nullLangMeansAllLanguages(@TempDir Path dir) throws Exception {
        Files.createDirectories(dir.resolve("renders"));
        Files.writeString(dir.resolve("renders/tr.mp4"), "mp4");
        MetaApiClient client = new MetaApiClient(
                new MetaApiClientTest.FakeHttp(), "USERTOK", "PAGE1", "IGUSER", 0);
        MetaReelsPublisher pub = new MetaReelsPublisher("INSTAGRAM", client, null);

        Publication result = pub.publish(Job.create("ch1"), variant("tr"), null, dir);

        assertEquals("PUBLISHED", result.getStatus());
    }
}
