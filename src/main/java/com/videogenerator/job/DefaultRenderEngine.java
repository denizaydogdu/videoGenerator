package com.videogenerator.job;

import com.videogenerator.processor.AudioProcessor;
import com.videogenerator.processor.KenBurnsRenderer;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

/**
 * Production RenderEngine: mixes voiceover + music (with ducking, see
 * AudioProcessor settings) and renders the Ken Burns video.
 */
public class DefaultRenderEngine implements JobPipeline.RenderEngine {
    private final AudioProcessor audioProcessor;
    private final KenBurnsRenderer kenBurns;

    public DefaultRenderEngine(AudioProcessor audioProcessor, KenBurnsRenderer kenBurns) {
        this.audioProcessor = audioProcessor;
        this.kenBurns = kenBurns;
    }

    @Override
    public File render(List<File> images, double[] durations, File voiceover,
                       File music, File assFile, Path out) throws Exception {
        if (music == null) {
            // Voice-only mode: skip mixing, feed the voiceover directly
            return kenBurns.render(images, durations, voiceover, assFile, out);
        }
        // Job-scoped mix name: AudioProcessor writes into the shared temp/
        // dir, so the name must be unique across concurrent jobs.
        String jobId = out.getParent().getParent().getFileName().toString();
        String mixName = jobId + "-" + out.getFileName().toString().replace(".mp4", "-mix");
        File mixed = audioProcessor.mixVoiceoverAndMusic(voiceover, music, mixName);
        return kenBurns.render(images, durations, mixed, assFile, out);
    }
}
