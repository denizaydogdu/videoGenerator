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
 * Instagram Reels ("INSTAGRAM") ve Facebook Reels ("FACEBOOK") yayıncısı —
 * ikisi de tek MetaApiClient'ı paylaşır (aynı token/sayfa). Caption:
 * başlık + açıklama + hashtag'ler (YouTube'un aksine Meta'da hashtag'ler
 * caption içinde yaşar).
 */
public class MetaReelsPublisher implements Publisher {
    private static final Logger logger = LoggerFactory.getLogger(MetaReelsPublisher.class);
    /** IG caption sınırı 2200; güvenli pay bırakıyoruz. */
    private static final int MAX_CAPTION = 2000;

    private final String platform; // "INSTAGRAM" | "FACEBOOK"
    private final MetaApiClient client;

    public MetaReelsPublisher(String platform, MetaApiClient client) {
        if (!"INSTAGRAM".equals(platform) && !"FACEBOOK".equals(platform)) {
            throw new IllegalArgumentException("Unsupported Meta platform: " + platform);
        }
        this.platform = platform;
        this.client = client;
    }

    @Override
    public String platform() {
        return platform;
    }

    @Override
    public Publication publish(Job job, LangVariant variant, ChannelProfile profile,
                               Path jobDir) throws Exception {
        Path render = jobDir.resolve(variant.getRenderFile());
        if (!Files.isRegularFile(render)) {
            throw new IllegalStateException("Render file missing: " + render);
        }
        VideoMetadata md = variant.getMetadata();
        if (md == null || !md.isValid()) {
            throw new IllegalStateException(
                    "Variant metadata invalid for lang " + variant.getLang());
        }
        String caption = buildCaption(md);
        logger.info("Uploading {} [{}] to {}...", job.getJobId(), variant.getLang(), platform);
        String url = "INSTAGRAM".equals(platform)
                ? client.publishInstagramReel(render, caption)
                : client.publishFacebookReel(render, caption);

        Publication pub = new Publication();
        pub.setPlatform(platform);
        pub.setStatus("PUBLISHED");
        pub.setUrl(url);
        return pub;
    }

    static String buildCaption(VideoMetadata md) {
        StringBuilder caption = new StringBuilder(md.getTitle());
        if (md.getDescription() != null && !md.getDescription().isBlank()) {
            caption.append("\n\n").append(md.getDescription());
        }
        if (md.getHashtags() != null && !md.getHashtags().isEmpty()) {
            caption.append("\n\n").append(String.join(" ", md.getHashtags()));
        }
        String out = caption.toString();
        return out.length() <= MAX_CAPTION ? out : out.substring(0, MAX_CAPTION);
    }
}
