package com.videogenerator.publish;

import com.google.api.services.youtube.model.Video;
import com.videogenerator.api.YouTubeApiClient;
import com.videogenerator.channel.ChannelProfile;
import com.videogenerator.channel.ChannelStoreTest;
import com.videogenerator.job.Job;
import com.videogenerator.model.LangVariant;
import com.videogenerator.model.Publication;
import com.videogenerator.model.UploadResult;
import com.videogenerator.model.VideoMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class YouTubePublisherTest {

    private VideoMetadata metadata() {
        VideoMetadata md = new VideoMetadata();
        md.setTitle("Test Title");
        md.setDescription("Desc");
        md.setHashtags(List.of("#mystery", "#crime"));
        return md;
    }

    @Test
    void videoResourceCarriesSyntheticDeclaration() {
        Video video = YouTubeApiClient.buildVideoResource(
                metadata(), "10", "public", true);
        assertEquals("Test Title", video.getSnippet().getTitle());
        assertEquals("10", video.getSnippet().getCategoryId());
        assertEquals("public", video.getStatus().getPrivacyStatus());
        assertEquals(Boolean.TRUE, video.getStatus().get("containsSyntheticMedia"));
        assertEquals(Boolean.FALSE, video.getStatus().getSelfDeclaredMadeForKids(),
                "audience beyanı zorunlu: çocuklara yönelik değil");
        assertTrue(video.getSnippet().getTags().contains("mystery")); // # olmadan
    }

    @Test
    void videoResourceCarriesLanguageTargeting() {
        VideoMetadata md = metadata();
        md.setLanguage("es"); // dil etiketi algoritma hedeflemesi için kritik
        Video video = YouTubeApiClient.buildVideoResource(md, "10", "public", true);
        assertEquals("es", video.getSnippet().getDefaultLanguage());
        assertEquals("es", video.getSnippet().getDefaultAudioLanguage());
    }

    @Test
    void descriptionCarriesVisibleHashtagsAndShorts() {
        Video video = YouTubeApiClient.buildVideoResource(metadata(), "24", "public", true);
        String desc = video.getSnippet().getDescription();
        assertTrue(desc.contains("#mystery"), "hashtag'ler açıklamada görünür olmalı");
        assertTrue(desc.contains("#crime"), desc);
        assertTrue(desc.contains("#Shorts"), "#Shorts etiketi eklenmiş olmalı");
        assertTrue(desc.startsWith("Desc"), "asıl açıklama başta kalmalı");
    }

    @Test
    void missingLanguageOmitsTargeting() {
        Video video = YouTubeApiClient.buildVideoResource(metadata(), "10", "public", true);
        assertNull(video.getSnippet().getDefaultLanguage());
        assertNull(video.getSnippet().getDefaultAudioLanguage());
    }

    @Test
    void publishUploadsRenderAndReturnsPublication(@TempDir Path dir) throws Exception {
        Files.createDirectories(dir.resolve("renders"));
        Files.writeString(dir.resolve("renders/en.mp4"), "mp4");

        AtomicReference<File> uploaded = new AtomicReference<>();
        YouTubePublisher.UploaderFactory factory = profile -> (file, md) -> {
            uploaded.set(file);
            UploadResult r = new UploadResult();
            r.setVideoId("abc123");
            r.setUrl("https://youtube.com/watch?v=abc123");
            return r;
        };
        YouTubePublisher publisher = new YouTubePublisher(factory);

        Job job = Job.create("ch1");
        LangVariant v = new LangVariant();
        v.setLang("en");
        v.setMetadata(metadata());
        v.setRenderFile("renders/en.mp4");
        ChannelProfile profile = new com.google.gson.Gson()
                .fromJson(ChannelStoreTest.VALID, ChannelProfile.class);

        Publication pub = publisher.publish(job, v, profile, dir);

        assertEquals("YOUTUBE", pub.getPlatform());
        assertEquals("PUBLISHED", pub.getStatus());
        assertEquals("https://youtube.com/watch?v=abc123", pub.getUrl());
        assertTrue(uploaded.get().getPath().endsWith("renders/en.mp4"));
    }

    @Test
    void missingRenderFileFails(@TempDir Path dir) {
        YouTubePublisher publisher = new YouTubePublisher(
                profile -> (file, md) -> { throw new AssertionError("should not upload"); });
        Job job = Job.create("ch1");
        LangVariant v = new LangVariant();
        v.setLang("en");
        v.setMetadata(metadata());
        v.setRenderFile("renders/en.mp4"); // dosya yok
        ChannelProfile profile = new com.google.gson.Gson()
                .fromJson(ChannelStoreTest.VALID, ChannelProfile.class);

        assertThrows(IllegalStateException.class,
                () -> publisher.publish(job, v, profile, dir));
    }
}
