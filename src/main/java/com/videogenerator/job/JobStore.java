package com.videogenerator.job;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;

/**
 * File-based job persistence. One directory per job, job.json is the single
 * source of truth, written atomically (temp file + rename).
 */
public class JobStore {
    private static final Logger logger = LoggerFactory.getLogger(JobStore.class);
    private final Path root;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public JobStore(Path root) {
        this.root = root;
    }

    public Path dirFor(String jobId) {
        if (jobId == null || jobId.contains("/") || jobId.contains("\\")
                || jobId.contains("..")) {
            throw new IllegalArgumentException("Invalid jobId: " + jobId);
        }
        Path dir = root.resolve(jobId).normalize();
        if (!dir.startsWith(root.normalize())) {
            throw new IllegalArgumentException("Job path escapes root: " + jobId);
        }
        return dir;
    }

    public synchronized void save(Job job) {
        try {
            Path dir = dirFor(job.getJobId());
            Files.createDirectories(dir);
            Path tmp = dir.resolve("job.json.tmp");
            Files.writeString(tmp, gson.toJson(job));
            Files.move(tmp, dir.resolve("job.json"),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save job " + job.getJobId(), e);
        }
    }

    public Job load(String jobId) {
        try {
            Job job = gson.fromJson(
                    Files.readString(dirFor(jobId).resolve("job.json")), Job.class);
            if (job == null) {
                throw new IllegalStateException("Empty job.json for " + jobId);
            }
            job.normalize();
            return job;
        } catch (IOException | JsonSyntaxException e) {
            throw new RuntimeException("Failed to load job " + jobId, e);
        }
    }

    /**
     * Lists all jobs, newest first. Unreadable job directories are skipped
     * with a warning so one corrupt job cannot take down listing.
     */
    public List<Job> list() {
        try (var dirs = Files.list(root)) {
            return dirs.filter(Files::isDirectory)
                    .filter(d -> Files.exists(d.resolve("job.json")))
                    .map(d -> {
                        try {
                            return load(d.getFileName().toString());
                        } catch (RuntimeException e) {
                            logger.warn("Skipping unreadable job {}: {}",
                                    d.getFileName(), e.getMessage());
                            return null;
                        }
                    })
                    .filter(j -> j != null)
                    .sorted(Comparator.comparing(Job::getJobId).reversed())
                    .toList();
        } catch (IOException e) {
            logger.warn("Cannot list jobs in {}: {}", root, e.getMessage());
            return List.of();
        }
    }
}
