package com.videogenerator.web;

import com.videogenerator.job.Job;
import com.videogenerator.model.LangVariant;
import com.videogenerator.model.Publication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bir işin yayınlanmış varyantları için platform izlenmelerini toplar.
 * Sağlayıcı hataları satırı düşürmez — views=null döner (UI "—" gösterir);
 * tek platformun API arızası tüm tabloyu kilitleyemez.
 */
public class StatsCollector {
    private static final Logger logger = LoggerFactory.getLogger(StatsCollector.class);
    private static final Pattern YT = Pattern.compile("[?&]v=([\\w-]+)");
    private static final Pattern FB_REEL = Pattern.compile("/reel/(\\d+)");

    /** Platform başına: yayın URL'si -> izlenme (bilinmiyorsa null). */
    public interface ViewsProvider {
        Long views(String url) throws Exception;
    }

    public record Row(String lang, String platform, String url, Long views) {
    }

    private final Map<String, ViewsProvider> providers;

    public StatsCollector(Map<String, ViewsProvider> providers) {
        this.providers = providers;
    }

    public List<Row> collect(Job job) {
        List<Row> rows = new ArrayList<>();
        for (LangVariant variant : job.getVariants()) {
            if (variant.getPublications() == null) {
                continue;
            }
            for (Publication pub : variant.getPublications()) {
                if (!"PUBLISHED".equals(pub.getStatus()) || pub.getUrl() == null) {
                    continue;
                }
                ViewsProvider provider = providers.get(pub.getPlatform());
                if (provider == null) {
                    continue;
                }
                Long views = null;
                try {
                    views = provider.views(pub.getUrl());
                } catch (Exception e) {
                    logger.warn("Stats fetch failed for {} {}: {}",
                            pub.getPlatform(), pub.getUrl(), e.getMessage());
                }
                rows.add(new Row(variant.getLang(), pub.getPlatform(), pub.getUrl(), views));
            }
        }
        return rows;
    }

    public static String youtubeId(String url) {
        Matcher m = YT.matcher(url);
        return m.find() ? m.group(1) : null;
    }

    public static String fbReelId(String url) {
        Matcher m = FB_REEL.matcher(url);
        return m.find() ? m.group(1) : null;
    }
}
