package com.videogenerator.job;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class JobStoreTest {
    @Test
    void saveIsAtomicAndRoundTrips(@TempDir Path root) throws Exception {
        JobStore store = new JobStore(root);
        Job job = Job.create("truecrime-en");
        job.setStatus(JobStatus.RENDERING);
        store.save(job);

        assertFalse(Files.exists(store.dirFor(job.getJobId()).resolve("job.json.tmp")),
                "temp file must be renamed away");
        Job loaded = store.load(job.getJobId());
        assertEquals(JobStatus.RENDERING, loaded.getStatus());
        assertEquals("truecrime-en", loaded.getChannelId());
        assertEquals(1, store.list().size());
    }

    @Test
    void rejectsPathTraversalJobIds(@TempDir Path root) {
        JobStore store = new JobStore(root);
        assertThrows(IllegalArgumentException.class, () -> store.dirFor("../evil"));
        assertThrows(IllegalArgumentException.class, () -> store.dirFor("a/b"));
    }

    @Test
    void loadNormalizesMissingCollections(@TempDir Path root) throws Exception {
        Path dir = root.resolve("2026-08-01-120000-abcd1234");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("job.json"),
            "{\"jobId\":\"2026-08-01-120000-abcd1234\",\"channelId\":\"ch\",\"status\":\"DRAFTING\"}");
        Job job = new JobStore(root).load("2026-08-01-120000-abcd1234");
        assertNotNull(job.getCost());
        assertNotNull(job.getVariants());
        assertEquals(0.0, job.getCost().total(), 1e-9);
    }

    @Test
    void costTotalSums(@TempDir Path root) {
        CostBreakdown c = new CostBreakdown();
        c.setImages(0.48); c.setTts(0.30); c.setMusic(0.22); c.setLlm(0.01);
        assertEquals(1.01, c.total(), 1e-9);
    }
}
