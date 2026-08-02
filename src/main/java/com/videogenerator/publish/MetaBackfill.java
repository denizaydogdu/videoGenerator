package com.videogenerator.publish;

import com.videogenerator.channel.ChannelProfile;
import com.videogenerator.channel.ChannelStore;
import com.videogenerator.job.Job;
import com.videogenerator.job.JobStore;
import com.videogenerator.model.LangVariant;
import com.videogenerator.model.Publication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Geriye dönük Meta yayını: zaten YAYINLANMIŞ bir işin tek dil varyantını
 * verilen platformlara (IG/FB) gönderir. İdempotent — varyantta o platform
 * için kayıtlı yayın varsa atlar. Yeni yayınlar job.json'a işlenir; yarım
 * kalırsa aynı komut kaldığı yerden devam eder.
 */
public final class MetaBackfill {
    private static final Logger logger = LoggerFactory.getLogger(MetaBackfill.class);

    private MetaBackfill() {
    }

    public static List<Publication> run(JobStore jobStore, ChannelStore channelStore,
                                        String jobId, String lang,
                                        Map<String, Publisher> publishers) throws Exception {
        Job job = jobStore.load(jobId);
        ChannelProfile profile = channelStore.load(job.getChannelId());
        LangVariant variant = job.getVariants().stream()
                .filter(v -> lang.equals(v.getLang()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Job " + jobId + " has no variant for lang " + lang));

        List<Publication> added = new ArrayList<>();
        for (Map.Entry<String, Publisher> entry : publishers.entrySet()) {
            String platform = entry.getKey();
            boolean already = variant.getPublications() != null
                    && variant.getPublications().stream()
                    .anyMatch(p -> platform.equals(p.getPlatform())
                            && "PUBLISHED".equals(p.getStatus()));
            if (already) {
                logger.info("Backfill skip: {} [{}] already on {}", jobId, lang, platform);
                continue;
            }
            Publication pub = entry.getValue().publish(job, variant, profile,
                    jobStore.dirFor(jobId));
            variant.getPublications().add(pub);
            jobStore.save(job); // marker hemen kalıcı — yarım kalan koşu tekrarlamaz
            added.add(pub);
            logger.info("Backfill published: {} [{}] -> {}", jobId, lang, pub.getUrl());
        }
        return added;
    }
}
