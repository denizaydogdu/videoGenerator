package com.videogenerator.web;

import com.videogenerator.channel.ChannelStore;
import com.videogenerator.job.CostTracker;
import com.videogenerator.job.Job;
import com.videogenerator.job.JobStatus;
import com.videogenerator.job.JobStore;
import com.videogenerator.model.LangVariant;
import com.videogenerator.model.VideoMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class JobServiceTest {

    static Job pendingJob(JobStore store, String channelId, String title) {
        Job job = Job.create(channelId);
        LangVariant v = new LangVariant();
        v.setLang("en");
        VideoMetadata md = new VideoMetadata();
        md.setTitle(title);
        md.setDescription("desc");
        md.setHashtags(List.of("#x"));
        v.setMetadata(md);
        v.setDurationSeconds(72.5);
        job.getVariants().add(v);
        job.setStatus(JobStatus.PENDING_REVIEW);
        store.save(job);
        return job;
    }

    static JobService service(Path root) throws Exception {
        Path channels = root.resolve("channels");
        Files.createDirectories(channels);
        return new JobService(new JobStore(root.resolve("jobs")),
                new ChannelStore(channels),
                new CostTracker(root.resolve("costs")), 100.0);
    }

    @Test
    void listsAndFiltersJobs(@TempDir Path root) throws Exception {
        JobService svc = service(root);
        JobStore store = new JobStore(root.resolve("jobs"));
        pendingJob(store, "ch1", "Title A");
        Job b = pendingJob(store, "ch2", "Title B");
        b.setStatus(JobStatus.REJECTED);
        store.save(b);

        assertEquals(2, svc.listJobs(null, null).size());
        List<JobSummary> onlyCh1 = svc.listJobs("ch1", null);
        assertEquals(1, onlyCh1.size());
        assertEquals("Title A", onlyCh1.get(0).getTitle());
        assertEquals(72.5, onlyCh1.get(0).getDurationSeconds(), 1e-9);
        assertEquals(1, svc.listJobs(null, "REJECTED").size());
    }

    @Test
    void updateMetadataValidates(@TempDir Path root) throws Exception {
        JobService svc = service(root);
        JobStore store = new JobStore(root.resolve("jobs"));
        Job job = pendingJob(store, "ch1", "Old");

        VideoMetadata good = new VideoMetadata();
        good.setTitle("New Title");
        good.setDescription("New desc");
        good.setHashtags(List.of("#a", "#b"));
        svc.updateMetadata(job.getJobId(), "en", good);
        assertEquals("New Title",
                store.load(job.getJobId()).getVariants().get(0).getMetadata().getTitle());

        VideoMetadata bad = new VideoMetadata(); // boş → isValid false
        assertThrows(IllegalArgumentException.class,
                () -> svc.updateMetadata(job.getJobId(), "en", bad));
        assertThrows(IllegalArgumentException.class,
                () -> svc.updateMetadata(job.getJobId(), "xx", good));
    }

    @Test
    void approveAndRejectFollowStateMachine(@TempDir Path root) throws Exception {
        JobService svc = service(root);
        JobStore store = new JobStore(root.resolve("jobs"));
        Job job = pendingJob(store, "ch1", "T");

        assertThrows(IllegalArgumentException.class,
                () -> svc.approve(job.getJobId(), List.of())); // platform şart

        svc.approve(job.getJobId(), List.of("YOUTUBE"));
        Job approved = store.load(job.getJobId());
        assertEquals(JobStatus.APPROVED, approved.getStatus());
        assertEquals(List.of("YOUTUBE"), approved.getApprovedPlatforms());

        // İkinci approve: artık PENDING_REVIEW değil → 409'a eşlenecek
        assertThrows(IllegalStateException.class,
                () -> svc.approve(job.getJobId(), List.of("YOUTUBE")));

        Job other = pendingJob(store, "ch1", "T2");
        svc.reject(other.getJobId());
        assertEquals(JobStatus.REJECTED, store.load(other.getJobId()).getStatus());
        assertThrows(IllegalStateException.class, () -> svc.reject(other.getJobId()));
    }

    @Test
    void statsExposeSpendAndBudget(@TempDir Path root) throws Exception {
        JobService svc = service(root);
        new CostTracker(root.resolve("costs")).add(12.5);
        JobService.Stats stats = svc.stats();
        assertEquals(12.5, stats.spentThisMonth(), 1e-9);
        assertEquals(100.0, stats.monthlyBudget(), 1e-9);
    }
}
