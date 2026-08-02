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

class PublishServiceTest {

    private Job approvedJob(JobStore store, String channelId, String... langs) {
        Job job = Job.create(channelId);
        for (String lang : langs) {
            LangVariant v = new LangVariant();
            v.setLang(lang);
            VideoMetadata md = new VideoMetadata();
            md.setTitle("T-" + lang);
            md.setDescription("D");
            md.setHashtags(List.of("#x"));
            v.setMetadata(md);
            v.setRenderFile("renders/" + lang + ".mp4");
            v.setPublications(new ArrayList<>());
            job.getVariants().add(v);
        }
        job.setStatus(JobStatus.APPROVED);
        job.setApprovedPlatforms(List.of("YOUTUBE"));
        store.save(job);
        return job;
    }

    private ChannelStore channels(Path root) throws Exception {
        Path dir = root.resolve("channels");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("ch1.json"),
                ChannelStoreTest.VALID.replace("truecrime-en", "ch1"));
        return new ChannelStore(dir);
    }

    private Publisher countingPublisher(AtomicInteger calls, String urlPrefix) {
        return new Publisher() {
            @Override
            public String platform() {
                return "YOUTUBE";
            }

            @Override
            public Publication publish(Job job, LangVariant variant,
                                       com.videogenerator.channel.ChannelProfile profile,
                                       Path jobDir) {
                calls.incrementAndGet();
                Publication p = new Publication();
                p.setPlatform("YOUTUBE");
                p.setStatus("PUBLISHED");
                p.setUrl(urlPrefix + variant.getLang());
                return p;
            }
        };
    }

    @Test
    void publishesAllVariantsAndMarksPublished(@TempDir Path root) throws Exception {
        JobStore store = new JobStore(root.resolve("jobs"));
        Job job = approvedJob(store, "ch1", "en", "es");
        AtomicInteger calls = new AtomicInteger();
        PublishService svc = new PublishService(store, channels(root),
                Map.of("YOUTUBE", countingPublisher(calls, "https://yt/")));

        Job done = svc.publishApproved(job.getJobId());

        assertEquals(JobStatus.PUBLISHED, done.getStatus());
        assertEquals(2, calls.get());
        assertEquals("https://yt/en",
                done.getVariants().get(0).getPublications().get(0).getUrl());
    }

    @Test
    void rerunIsIdempotent(@TempDir Path root) throws Exception {
        JobStore store = new JobStore(root.resolve("jobs"));
        Job job = approvedJob(store, "ch1", "en");
        AtomicInteger calls = new AtomicInteger();
        PublishService svc = new PublishService(store, channels(root),
                Map.of("YOUTUBE", countingPublisher(calls, "https://yt/")));

        svc.publishApproved(job.getJobId());
        // PUBLISHED işten yeniden yayın: durum hatası
        assertThrows(IllegalStateException.class,
                () -> svc.publishApproved(job.getJobId()));
        assertEquals(1, calls.get());
    }

    @Test
    void partialFailureKeepsPublishingAndResumesOnlyMissing(@TempDir Path root) throws Exception {
        JobStore store = new JobStore(root.resolve("jobs"));
        Job job = approvedJob(store, "ch1", "en", "es");
        AtomicInteger calls = new AtomicInteger();
        Publisher failsOnEs = new Publisher() {
            @Override
            public String platform() {
                return "YOUTUBE";
            }

            @Override
            public Publication publish(Job j, LangVariant v,
                                       com.videogenerator.channel.ChannelProfile p, Path d) {
                calls.incrementAndGet();
                if ("es".equals(v.getLang())) {
                    throw new RuntimeException("quota exceeded");
                }
                Publication pub = new Publication();
                pub.setPlatform("YOUTUBE");
                pub.setStatus("PUBLISHED");
                pub.setUrl("https://yt/en");
                return pub;
            }
        };
        PublishService broken = new PublishService(store, channels(root),
                Map.of("YOUTUBE", failsOnEs));

        assertThrows(RuntimeException.class,
                () -> broken.publishApproved(job.getJobId()));
        Job partial = store.load(job.getJobId());
        assertEquals(JobStatus.PUBLISHING, partial.getStatus());
        assertNotNull(partial.getError());
        assertEquals(2, calls.get());

        // Resume: yalnız es yeniden denenir
        AtomicInteger resumeCalls = new AtomicInteger();
        PublishService fixed = new PublishService(store, channels(root),
                Map.of("YOUTUBE", countingPublisher(resumeCalls, "https://yt/")));
        Job done = fixed.publishApproved(job.getJobId());
        assertEquals(JobStatus.PUBLISHED, done.getStatus());
        assertEquals(1, resumeCalls.get());
        assertNull(done.getError());
    }

    @Test
    void dailyUploadLimitBlocksAndResumes(@TempDir Path root) throws Exception {
        JobStore store = new JobStore(root.resolve("jobs"));
        Job job = approvedJob(store, "ch1", "en", "es");
        AtomicInteger calls = new AtomicInteger();
        UploadCounter counter = new UploadCounter(root.resolve("costs"));
        PublishService limited = new PublishService(store, channels(root),
                Map.of("YOUTUBE", countingPublisher(calls, "https://yt/")),
                counter, 1); // günlük limit 1

        assertThrows(IllegalStateException.class,
                () -> limited.publishApproved(job.getJobId()));
        assertEquals(1, calls.get(), "limit sonrası upload durmalı");
        assertEquals(JobStatus.PUBLISHING, store.load(job.getJobId()).getStatus());

        // "Ertesi gün": yeni sayaç dizini = sıfır sayaç
        PublishService fresh = new PublishService(store, channels(root),
                Map.of("YOUTUBE", countingPublisher(calls, "https://yt/")),
                new UploadCounter(root.resolve("costs2")), 10);
        Job done = fresh.publishApproved(job.getJobId());
        assertEquals(JobStatus.PUBLISHED, done.getStatus());
        assertEquals(2, calls.get(), "yalnız eksik varyant yüklenmeli");
    }

    @Test
    void dailyLimitAppliesOnlyToYoutubeNotMeta(@TempDir Path root) throws Exception {
        // Sayaç YouTube API kotası içindir; IG/FB yayınları onu ne yakmalı
        // ne de ona takılmalı
        JobStore store = new JobStore(root.resolve("jobs"));
        Job job = approvedJob(store, "ch1", "en");
        job.setApprovedPlatforms(List.of("YOUTUBE", "INSTAGRAM", "FACEBOOK"));
        store.save(job);
        AtomicInteger yt = new AtomicInteger();
        AtomicInteger meta = new AtomicInteger();
        Publisher ig = simplePublisher("INSTAGRAM", meta);
        Publisher fb = simplePublisher("FACEBOOK", meta);
        UploadCounter counter = new UploadCounter(root.resolve("costs"));
        PublishService svc = new PublishService(store, channels(root),
                Map.of("YOUTUBE", countingPublisher(yt, "https://yt/"),
                        "INSTAGRAM", ig, "FACEBOOK", fb),
                counter, 1); // YouTube limiti 1

        Job done = svc.publishApproved(job.getJobId());

        assertEquals(JobStatus.PUBLISHED, done.getStatus());
        assertEquals(1, yt.get());
        assertEquals(2, meta.get(), "Meta yayınları limit 1'e takılmamalı");
        assertEquals(1, counter.today(), "sayaç yalnız YouTube'u saymalı");
    }

    private Publisher simplePublisher(String platform, AtomicInteger calls) {
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
                p.setUrl("https://" + platform.toLowerCase() + "/x");
                return p;
            }
        };
    }

    @Test
    void missingPublisherRecordsSkip(@TempDir Path root) throws Exception {
        JobStore store = new JobStore(root.resolve("jobs"));
        Job job = approvedJob(store, "ch1", "en");
        job.setApprovedPlatforms(List.of("YOUTUBE", "TIKTOK"));
        store.save(job);
        AtomicInteger calls = new AtomicInteger();
        PublishService svc = new PublishService(store, channels(root),
                Map.of("YOUTUBE", countingPublisher(calls, "https://yt/")));

        Job done = svc.publishApproved(job.getJobId());

        assertEquals(JobStatus.PUBLISHED, done.getStatus());
        List<Publication> pubs = done.getVariants().get(0).getPublications();
        assertEquals(2, pubs.size());
        assertTrue(pubs.stream().anyMatch(p ->
                "TIKTOK".equals(p.getPlatform())
                        && "SKIPPED_NO_PUBLISHER".equals(p.getStatus())));
    }
}
