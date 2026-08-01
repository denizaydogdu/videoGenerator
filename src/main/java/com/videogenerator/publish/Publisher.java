package com.videogenerator.publish;

import com.videogenerator.channel.ChannelProfile;
import com.videogenerator.job.Job;
import com.videogenerator.model.LangVariant;
import com.videogenerator.model.Publication;

import java.nio.file.Path;

/**
 * One platform's upload implementation. Implementations must be safe to
 * call once per variant; idempotency across runs is handled by
 * {@link PublishService} via recorded publications.
 */
public interface Publisher {
    /** Platform key as stored in channel profiles, e.g. "YOUTUBE". */
    String platform();

    Publication publish(Job job, LangVariant variant, ChannelProfile profile,
                        Path jobDir) throws Exception;
}
