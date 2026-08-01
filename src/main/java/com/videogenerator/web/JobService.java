package com.videogenerator.web;

import com.videogenerator.channel.ChannelStore;
import com.videogenerator.job.CostTracker;
import com.videogenerator.job.Job;
import com.videogenerator.job.JobStatus;
import com.videogenerator.job.JobStore;
import com.videogenerator.model.LangVariant;
import com.videogenerator.model.VideoMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Backoffice domain operations over the job store. No HTTP concerns here,
 * so the whole review workflow is unit-testable.
 */
public class JobService {
    private static final Logger logger = LoggerFactory.getLogger(JobService.class);

    /** Monthly spend snapshot for the budget bar. */
    public record Stats(double spentThisMonth, double monthlyBudget) {
    }

    private final JobStore jobStore;
    private final ChannelStore channelStore;
    private final CostTracker costTracker;
    private final double monthlyBudget;

    public JobService(JobStore jobStore, ChannelStore channelStore,
                      CostTracker costTracker, double monthlyBudget) {
        this.jobStore = jobStore;
        this.channelStore = channelStore;
        this.costTracker = costTracker;
        this.monthlyBudget = monthlyBudget;
    }

    public ChannelStore channels() {
        return channelStore;
    }

    public JobStore jobs() {
        return jobStore;
    }

    public List<JobSummary> listJobs(String channelFilter, String statusFilter) {
        return jobStore.list().stream()
                .filter(j -> channelFilter == null || channelFilter.equals(j.getChannelId()))
                .filter(j -> statusFilter == null
                        || j.getStatus() == JobStatus.valueOf(statusFilter))
                .map(this::summarize)
                .toList();
    }

    private JobSummary summarize(Job job) {
        JobSummary s = new JobSummary();
        s.setJobId(job.getJobId());
        s.setChannelId(job.getChannelId());
        s.setStatus(job.getStatus());
        s.setCostTotal(job.getCost().total());
        s.setLangs(job.getVariants().stream().map(LangVariant::getLang).toList());
        if (!job.getVariants().isEmpty()) {
            LangVariant first = job.getVariants().get(0);
            s.setTitle(first.getMetadata() != null ? first.getMetadata().getTitle() : null);
            s.setDurationSeconds(first.getDurationSeconds());
        }
        if (s.getTitle() == null && job.getStory() != null) {
            s.setTitle(job.getStory().getTitle());
        }
        s.setSceneCount(job.getStory() != null && job.getStory().getScenes() != null
                ? job.getStory().getScenes().size() : 0);
        return s;
    }

    public Job detail(String jobId) {
        return jobStore.load(jobId);
    }

    public synchronized void updateMetadata(String jobId, String lang, VideoMetadata metadata) {
        if (metadata == null || !metadata.isValid()) {
            throw new IllegalArgumentException("Metadata is invalid (title/description/hashtags required)");
        }
        Job job = jobStore.load(jobId);
        if (job.getStatus() != JobStatus.PENDING_REVIEW) {
            throw new IllegalStateException(
                    "Metadata can only be edited while PENDING_REVIEW (was " + job.getStatus() + ")");
        }
        LangVariant variant = job.getVariants().stream()
                .filter(v -> lang.equals(v.getLang()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No variant for language: " + lang));
        variant.setMetadata(metadata);
        jobStore.save(job);
        logger.info("Metadata updated: {} [{}] -> {}", jobId, lang, metadata.getTitle());
    }

    /**
     * Story-level approval: marks the whole job APPROVED for the selected
     * platforms. Publishing itself happens in the publisher layer (Plan 3).
     */
    public synchronized void approve(String jobId, List<String> platforms) {
        if (platforms == null || platforms.isEmpty()) {
            throw new IllegalArgumentException("At least one platform is required");
        }
        Job job = jobStore.load(jobId);
        if (job.getStatus() != JobStatus.PENDING_REVIEW) {
            throw new IllegalStateException(
                    "Only PENDING_REVIEW jobs can be approved (was " + job.getStatus() + ")");
        }
        job.setApprovedPlatforms(List.copyOf(platforms));
        job.setStatus(JobStatus.APPROVED);
        jobStore.save(job);
        logger.info("Job approved: {} -> {}", jobId, platforms);
    }

    public synchronized void reject(String jobId) {
        Job job = jobStore.load(jobId);
        if (job.getStatus() != JobStatus.PENDING_REVIEW) {
            throw new IllegalStateException(
                    "Only PENDING_REVIEW jobs can be rejected (was " + job.getStatus() + ")");
        }
        job.setStatus(JobStatus.REJECTED);
        jobStore.save(job);
        logger.info("Job rejected: {}", jobId);
    }

    public Stats stats() {
        return new Stats(costTracker.spentThisMonth(), monthlyBudget);
    }
}
