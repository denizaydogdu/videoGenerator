package com.videogenerator.publish;

import com.videogenerator.channel.ChannelProfile;
import com.videogenerator.channel.ChannelStore;
import com.videogenerator.job.Job;
import com.videogenerator.job.JobStatus;
import com.videogenerator.job.JobStore;
import com.videogenerator.model.LangVariant;
import com.videogenerator.model.Publication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Map;

/**
 * Publishes an approved job's variants to the platforms selected at
 * approval time. Idempotent: a PUBLISHED publication record is the skip
 * marker, and job.json is saved after every upload, so a crashed or
 * partially failed run resumes exactly where it stopped.
 *
 * Accepted limitations (single-user local tool):
 * - A crash in the milliseconds between a successful external upload and
 *   the marker save would re-upload on resume; platforms offer no
 *   two-phase commit to close this window.
 * - Cross-process locking is not implemented; one serve/publish process
 *   at a time (same assumption as BudgetGuard).
 * - SKIPPED_NO_PUBLISHER is a terminal outcome: the job can reach
 *   PUBLISHED with skips recorded. The UI prevents selecting platforms
 *   without publishers, so this is an escape hatch, not a normal path.
 */
public class PublishService {
    private static final Logger logger = LoggerFactory.getLogger(PublishService.class);

    private final JobStore jobStore;
    private final ChannelStore channelStore;
    private final Map<String, Publisher> publishers;
    private final UploadCounter uploadCounter;
    private final int dailyUploadLimit;

    /** Limitless variant (tests / non-YouTube future platforms). */
    public PublishService(JobStore jobStore, ChannelStore channelStore,
                          Map<String, Publisher> publishers) {
        this(jobStore, channelStore, publishers, null, Integer.MAX_VALUE);
    }

    /**
     * @param dailyUploadLimit hard cap on uploads per calendar day — protects
     *                         the channel from spam signals and API quota
     *                         (videos.insert has its own daily bucket)
     */
    public PublishService(JobStore jobStore, ChannelStore channelStore,
                          Map<String, Publisher> publishers,
                          UploadCounter uploadCounter, int dailyUploadLimit) {
        this.jobStore = jobStore;
        this.channelStore = channelStore;
        this.publishers = publishers;
        this.uploadCounter = uploadCounter;
        this.dailyUploadLimit = dailyUploadLimit;
    }

    public synchronized Job publishApproved(String jobId) {
        Job job = jobStore.load(jobId);
        if (job.getStatus() != JobStatus.APPROVED
                && job.getStatus() != JobStatus.PUBLISHING) {
            throw new IllegalStateException(
                    "Job must be APPROVED or PUBLISHING to publish (was "
                            + job.getStatus() + ")");
        }
        if (job.getApprovedPlatforms() == null || job.getApprovedPlatforms().isEmpty()) {
            throw new IllegalStateException("Job has no approved platforms");
        }
        ChannelProfile profile = channelStore.load(job.getChannelId());
        job.setStatus(JobStatus.PUBLISHING);
        jobStore.save(job);

        RuntimeException firstFailure = null;
        for (LangVariant variant : job.getVariants()) {
            if (variant.getPublications() == null) {
                variant.setPublications(new ArrayList<>());
            }
            for (String platform : job.getApprovedPlatforms()) {
                if (hasPublication(variant, platform)) {
                    logger.info("Skip already-handled {} [{}] on {}",
                            job.getJobId(), variant.getLang(), platform);
                    continue;
                }
                Publisher publisher = publishers.get(platform);
                if (publisher == null) {
                    Publication skip = new Publication();
                    skip.setPlatform(platform);
                    skip.setStatus("SKIPPED_NO_PUBLISHER");
                    variant.getPublications().add(skip);
                    jobStore.save(job);
                    logger.warn("No publisher for {} — recorded skip", platform);
                    continue;
                }
                if (uploadCounter != null && uploadCounter.today() >= dailyUploadLimit) {
                    // Limit dolunca durum PUBLISHING kalır; ertesi gün
                    // 'publish <jobId>' veya yeni approve tetiği devam ettirir
                    job.setError("Daily upload limit reached (" + dailyUploadLimit + ")");
                    jobStore.save(job);
                    throw new IllegalStateException(
                            "Daily upload limit reached (" + dailyUploadLimit + ")");
                }
                try {
                    Publication pub = publisher.publish(job, variant, profile,
                            jobStore.dirFor(job.getJobId()));
                    // Idempotency marker ÖNCE kalıcı olur; sayaç yazımı upload
                    // gerçekleştikten sonra asla ölümcül olamaz (aksi halde
                    // resume aynı videoyu ikinci kez yükler)
                    variant.getPublications().add(pub);
                    jobStore.save(job);
                    if (uploadCounter != null) {
                        try {
                            uploadCounter.increment();
                        } catch (RuntimeException e) {
                            logger.warn("Upload counter write failed (non-fatal)", e);
                        }
                    }
                    logger.info("Published {} [{}] -> {}",
                            job.getJobId(), variant.getLang(), pub.getUrl());
                } catch (Exception e) {
                    logger.error("Publish failed: {} [{}] on {}",
                            job.getJobId(), variant.getLang(), platform, e);
                    if (firstFailure == null) {
                        firstFailure = (e instanceof RuntimeException re)
                                ? re : new RuntimeException(e);
                    }
                }
            }
        }

        if (firstFailure != null) {
            job.setError("Publish failed: " + firstFailure.getMessage());
            jobStore.save(job); // durum PUBLISHING kalır — resume eksikleri dener
            throw firstFailure;
        }
        job.setError(null);
        job.setStatus(JobStatus.PUBLISHED);
        jobStore.save(job);
        logger.info("Job fully published: {}", job.getJobId());
        return job;
    }

    private boolean hasPublication(LangVariant variant, String platform) {
        return variant.getPublications().stream()
                .anyMatch(p -> platform.equals(p.getPlatform())
                        && ("PUBLISHED".equals(p.getStatus())
                            || "SKIPPED_NO_PUBLISHER".equals(p.getStatus())));
    }
}
