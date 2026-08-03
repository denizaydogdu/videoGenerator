package com.videogenerator.publish;

import com.videogenerator.channel.ChannelProfile;
import com.videogenerator.job.Job;
import com.videogenerator.model.LangVariant;
import com.videogenerator.model.Publication;
import com.videogenerator.model.VideoMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * TikTok Direct Post yayıncısı. Meta'daki gibi tek-dil koruması uygular
 * (aynı görsellerin çok dilli kopyaları tek hesapta duplicate sayılır).
 * Uygulama onaylanana dek privacyLevel SELF_ONLY olmalı (config).
 */
public class TikTokPublisher implements Publisher {
    private static final Logger logger = LoggerFactory.getLogger(TikTokPublisher.class);
    private static final int MAX_TITLE = 2000;

    private final TikTokApiClient client;
    private final String onlyLang;      // null = tüm diller
    private final String privacyLevel;  // SELF_ONLY | PUBLIC_TO_EVERYONE
    private final String profileUrl;    // yayın kaydında gösterilecek adres

    public TikTokPublisher(TikTokApiClient client, String onlyLang,
                           String privacyLevel, String profileUrl) {
        this.client = client;
        this.onlyLang = (onlyLang == null || onlyLang.isBlank()) ? null : onlyLang;
        this.privacyLevel = privacyLevel;
        this.profileUrl = profileUrl;
    }

    @Override
    public String platform() {
        return "TIKTOK";
    }

    @Override
    public Publication publish(Job job, LangVariant variant, ChannelProfile profile,
                               Path jobDir) throws Exception {
        if (onlyLang != null && !onlyLang.equals(variant.getLang())) {
            logger.info("Skipping {} [{}] — TIKTOK publishes only '{}'",
                    job.getJobId(), variant.getLang(), onlyLang);
            Publication skip = new Publication();
            skip.setPlatform("TIKTOK");
            skip.setStatus("SKIPPED_LANG");
            return skip;
        }
        Path render = jobDir.resolve(variant.getRenderFile());
        if (!Files.isRegularFile(render)) {
            throw new IllegalStateException("Render file missing: " + render);
        }
        VideoMetadata md = variant.getMetadata();
        if (md == null || !md.isValid()) {
            throw new IllegalStateException(
                    "Variant metadata invalid for lang " + variant.getLang());
        }
        String title = buildTitle(md);
        logger.info("Uploading {} [{}] to TikTok ({})...",
                job.getJobId(), variant.getLang(), privacyLevel);
        String publishId = client.directPost(render, title, privacyLevel);

        Publication pub = new Publication();
        pub.setPlatform("TIKTOK");
        pub.setStatus("PUBLISHED");
        pub.setUrl(profileUrl + "#" + publishId);
        return pub;
    }

    static String buildTitle(VideoMetadata md) {
        StringBuilder title = new StringBuilder(md.getTitle());
        if (md.getHashtags() != null && !md.getHashtags().isEmpty()) {
            title.append(" ").append(String.join(" ", md.getHashtags()));
        }
        String out = title.toString();
        return out.length() <= MAX_TITLE ? out : out.substring(0, MAX_TITLE);
    }
}
