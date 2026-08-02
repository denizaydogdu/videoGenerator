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
    private final String onlyLang; // null = tüm diller

    /** Kanalın kendi Meta hesabı varsa ona özel istemci üretir. */
    public interface ClientResolver {
        MetaApiClient forProfile(ChannelProfile profile) throws Exception;
    }

    private ClientResolver clientResolver; // null = her zaman varsayılan istemci

    public MetaReelsPublisher withClientResolver(ClientResolver resolver) {
        this.clientResolver = resolver;
        return this;
    }

    public MetaReelsPublisher(String platform, MetaApiClient client) {
        this(platform, client, null);
    }

    /**
     * @param onlyLang yalnız bu dil yayınlanır, diğer varyantlar SKIPPED_LANG
     *                 olarak kaydedilir. Meta'nın 2026 orijinallik kuralları
     *                 aynı görsellerin çok-dilli kopyalarını duplicate sayıp
     *                 hesabın keşfet erişimini kısabiliyor.
     */
    public MetaReelsPublisher(String platform, MetaApiClient client, String onlyLang) {
        if (!"INSTAGRAM".equals(platform) && !"FACEBOOK".equals(platform)) {
            throw new IllegalArgumentException("Unsupported Meta platform: " + platform);
        }
        this.platform = platform;
        this.client = client;
        this.onlyLang = (onlyLang == null || onlyLang.isBlank()) ? null : onlyLang;
    }

    @Override
    public String platform() {
        return platform;
    }

    @Override
    public Publication publish(Job job, LangVariant variant, ChannelProfile profile,
                               Path jobDir) throws Exception {
        // Kanal profili config'i ezer: profile.meta.publishLang > meta.publish.lang
        String effectiveLang = onlyLang;
        if (profile != null && profile.getMeta() != null
                && profile.getMeta().getPublishLang() != null
                && !profile.getMeta().getPublishLang().isBlank()) {
            effectiveLang = profile.getMeta().getPublishLang();
        }
        if (effectiveLang != null && !effectiveLang.equals(variant.getLang())) {
            logger.info("Skipping {} [{}] — {} publishes only '{}' (duplicate guard)",
                    job.getJobId(), variant.getLang(), platform, effectiveLang);
            Publication skip = new Publication();
            skip.setPlatform(platform);
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
        String caption = buildCaption(md);
        MetaApiClient effective = client;
        if (clientResolver != null && profile != null && profile.getMeta() != null
                && profile.getMeta().hasCustomAccount()) {
            effective = clientResolver.forProfile(profile);
        }
        logger.info("Uploading {} [{}] to {}...", job.getJobId(), variant.getLang(), platform);
        String url = "INSTAGRAM".equals(platform)
                ? effective.publishInstagramReel(render, caption)
                : effective.publishFacebookReel(render, caption);

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
