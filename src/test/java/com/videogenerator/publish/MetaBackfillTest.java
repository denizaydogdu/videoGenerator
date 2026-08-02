package com.videogenerator.publish;

import com.videogenerator.channel.ChannelStore;
import com.videogenerator.channel.ChannelStoreTest;
import com.videogenerator.job.Job;
import com.videogenerator.job.JobStatus;
import com.videogenerator.job.JobStore;
import com.videogenerator.model.LangVariant;
import com.videogenerator.model.Publication;
import com.videogenerator.model.VideoMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class MetaBackfillTest {

    private JobStore storeWithPublishedJob(Path root, String... existingPlatforms)
            throws Exception {
        JobStore store = new JobStore(root.resolve("jobs"));
        Job job = Job.create("ch1");
        LangVariant v = new LangVariant();
        v.setLang("en");
        VideoMetadata md = new VideoMetadata();
        md.setTitle("T");
        md.setDescription("D");
        md.setHashtags(List.of("#x"));
        v.setMetadata(md);
        v.setRenderFile("renders/en.mp4");
        v.setPublications(new ArrayList<>());
        for (String p : existingPlatforms) {
            Publication pub = new Publication();
            pub.setPlatform(p);
            pub.setStatus("PUBLISHED");
            pub.setUrl("https://old/" + p);
            v.getPublications().add(pub);
        }
        job.getVariants().add(v);
        job.setStatus(JobStatus.PUBLISHED);
        store.save(job);
        Files.createDirectories(store.dirFor(job.getJobId()).resolve("renders"));
        Files.writeString(store.dirFor(job.getJobId()).resolve("renders/en.mp4"), "mp4");
        // testin erişmesi için id'yi sakla
        Files.writeString(root.resolve("jobid.txt"), job.getJobId());
        return store;
    }

    private ChannelStore channels(Path root) throws Exception {
        Path dir = root.resolve("channels");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("ch1.json"),
                ChannelStoreTest.VALID.replace("truecrime-en", "ch1"));
        return new ChannelStore(dir);
    }

    private Publisher fake(String platform, AtomicInteger calls) {
        return new Publisher() {
            @Override
            public String platform() {
                return platform;
            }

            @Override
            public Publication publish(Job job, LangVariant variant,
                                       com.videogenerator.channel.ChannelProfile profile,
                                       Path jobDir) {
                calls.incrementAndGet();
                Publication p = new Publication();
                p.setPlatform(platform);
                p.setStatus("PUBLISHED");
                p.setUrl("https://new/" + platform);
                return p;
            }
        };
    }

    @Test
    void publishesOnlyMissingPlatformsAndPersists(@TempDir Path root) throws Exception {
        JobStore store = storeWithPublishedJob(root, "YOUTUBE", "INSTAGRAM");
        String jobId = Files.readString(root.resolve("jobid.txt"));
        AtomicInteger ig = new AtomicInteger();
        AtomicInteger fb = new AtomicInteger();

        List<Publication> added = MetaBackfill.run(store, channels(root), jobId, "en",
                Map.of("INSTAGRAM", fake("INSTAGRAM", ig), "FACEBOOK", fake("FACEBOOK", fb)));

        assertEquals(1, added.size());
        assertEquals("FACEBOOK", added.get(0).getPlatform());
        assertEquals(0, ig.get(), "IG zaten yayınlı — atlanmalı");
        assertEquals(1, fb.get());
        // kalıcılık: job.json'a yazılmış olmalı
        Job reloaded = store.load(jobId);
        assertTrue(reloaded.getVariants().get(0).getPublications().stream()
                .anyMatch(p -> "FACEBOOK".equals(p.getPlatform())
                        && "https://new/FACEBOOK".equals(p.getUrl())));
    }

    @Test
    void unknownLangFails(@TempDir Path root) throws Exception {
        JobStore store = storeWithPublishedJob(root, "YOUTUBE");
        String jobId = Files.readString(root.resolve("jobid.txt"));

        assertThrows(IllegalArgumentException.class, () ->
                MetaBackfill.run(store, channels(root), jobId, "de", Map.of()));
    }
}
