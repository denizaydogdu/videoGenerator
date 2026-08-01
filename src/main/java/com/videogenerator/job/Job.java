package com.videogenerator.job;

import com.videogenerator.model.LangVariant;
import com.videogenerator.model.Story;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * One generation job. Serialized as output/jobs/&lt;jobId&gt;/job.json.
 * All file paths inside are relative to the job directory.
 */
public class Job {
    private static final DateTimeFormatter ID_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss");

    private String jobId;
    private String channelId;
    private JobStatus status;
    private Story story;
    private String musicFile;
    private List<LangVariant> variants;
    private CostBreakdown cost;
    private String error;

    public static Job create(String channelId) {
        Job job = new Job();
        // 8 hex chars of UUID entropy: collision-safe even for same-second bursts
        job.jobId = LocalDateTime.now().format(ID_FORMAT)
                + "-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        job.channelId = channelId;
        job.status = JobStatus.DRAFTING;
        job.cost = new CostBreakdown();
        job.variants = new ArrayList<>();
        return job;
    }

    /**
     * Restores invariants after Gson deserialization: collections and cost
     * are never null even when loading older/partial job.json files.
     */
    public void normalize() {
        if (cost == null) {
            cost = new CostBreakdown();
        }
        if (variants == null) {
            variants = new ArrayList<>();
        }
    }

    public String getJobId() {
        return jobId;
    }

    public String getChannelId() {
        return channelId;
    }

    public JobStatus getStatus() {
        return status;
    }

    public void setStatus(JobStatus status) {
        this.status = status;
    }

    public Story getStory() {
        return story;
    }

    public void setStory(Story story) {
        this.story = story;
    }

    public String getMusicFile() {
        return musicFile;
    }

    public void setMusicFile(String musicFile) {
        this.musicFile = musicFile;
    }

    public List<LangVariant> getVariants() {
        return variants;
    }

    public CostBreakdown getCost() {
        return cost;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
