package com.videogenerator.publish;

import com.videogenerator.channel.ChannelProfile;
import com.videogenerator.job.Job;
import com.videogenerator.model.LangVariant;
import com.videogenerator.model.Publication;
import com.videogenerator.model.UploadResult;
import com.videogenerator.model.VideoMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * YouTube publisher. Uploads a language variant's render with the
 * variant's localized metadata. The uploader is created per channel
 * profile (separate OAuth tokens per channel) and cached.
 */
public class YouTubePublisher implements Publisher {
    private static final Logger logger = LoggerFactory.getLogger(YouTubePublisher.class);

    /** Minimal upload seam — real impl wraps YouTubeApiClient.uploadVideo. */
    public interface Uploader {
        UploadResult upload(File videoFile, VideoMetadata metadata) throws Exception;
    }

    /** Creates an uploader for a channel profile (may trigger OAuth). */
    public interface UploaderFactory {
        Uploader forProfile(ChannelProfile profile) throws Exception;
    }

    private final UploaderFactory factory;
    private final Map<String, Uploader> cache = new ConcurrentHashMap<>();

    public YouTubePublisher(UploaderFactory factory) {
        this.factory = factory;
    }

    @Override
    public String platform() {
        return "YOUTUBE";
    }

    @Override
    public Publication publish(Job job, LangVariant variant, ChannelProfile profile,
                               Path jobDir) throws Exception {
        File render = jobDir.resolve(variant.getRenderFile()).toFile();
        if (!render.isFile()) {
            throw new IllegalStateException("Render file missing: " + render);
        }
        if (variant.getMetadata() == null || !variant.getMetadata().isValid()) {
            throw new IllegalStateException(
                    "Variant metadata invalid for lang " + variant.getLang());
        }
        Uploader uploader = cache.computeIfAbsent(profile.getChannelId(), id -> {
            try {
                return factory.forProfile(profile);
            } catch (Exception e) {
                throw new RuntimeException("Cannot create YouTube uploader for " + id, e);
            }
        });
        logger.info("Uploading {} [{}] to YouTube...", job.getJobId(), variant.getLang());
        UploadResult result = uploader.upload(render, variant.getMetadata());

        Publication pub = new Publication();
        pub.setPlatform(platform());
        pub.setStatus("PUBLISHED");
        pub.setUrl(result.getUrl());
        return pub;
    }
}
