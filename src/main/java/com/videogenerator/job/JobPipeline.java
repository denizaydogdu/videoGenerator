package com.videogenerator.job;

import com.videogenerator.api.ElevenLabsClient;
import com.videogenerator.api.ImageGenerator;
import com.videogenerator.api.LlmClient;
import com.videogenerator.api.MusicGenerator;
import com.videogenerator.channel.ChannelProfile;
import com.videogenerator.channel.ChannelStore;
import com.videogenerator.model.ContentIdea;
import com.videogenerator.model.LangVariant;
import com.videogenerator.model.LocalizedStory;
import com.videogenerator.model.NicheData;
import com.videogenerator.model.Story;
import com.videogenerator.model.SubtitleCue;
import com.videogenerator.processor.SceneTimer;
import com.videogenerator.processor.SubtitleRenderer;
import com.videogenerator.service.IdeaGenerator;
import com.videogenerator.service.SceneImageService;
import com.videogenerator.service.StoryWriter;
import com.videogenerator.service.TranslationService;
import com.videogenerator.util.ApiException;
import com.videogenerator.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * End-to-end orchestrator: channel profile in, PENDING_REVIEW job out.
 *
 * Phase 1 (language-independent): idea -> story -> scene images -> music.
 * Phase 2 (per language): localize -> TTS with timestamps -> scene cuts ->
 * subtitles -> render. job.json is saved after every step (atomic), and
 * steps whose outputs already exist are skipped, so a crashed job resumes
 * without re-paying for finished work.
 */
public class JobPipeline {
    private static final Logger logger = LoggerFactory.getLogger(JobPipeline.class);

    /** TTS abstraction: adapter over ElevenLabsClient.generateWithTimestamps. */
    public interface TtsEngine {
        ElevenLabsClient.TtsResult speak(String text, String voiceId,
                                         Path audioOut, Path alignOut) throws ApiException;
    }

    /** Render abstraction: mixes audio and renders the final video. */
    public interface RenderEngine {
        File render(List<File> images, double[] durations, File voiceover,
                    File music, File assFile, Path out) throws Exception;
    }

    private final JobStore jobStore;
    private final ChannelStore channelStore;
    private final LlmClient llm;
    private final ImageGenerator imageGen;
    private final MusicGenerator musicGen;
    private final TtsEngine ttsEngine;
    private final RenderEngine renderEngine;
    private final BudgetGuard budgetGuard;
    private final CostTracker costTracker;
    private final IdeaGenerator ideaGenerator;

    public JobPipeline(JobStore jobStore, ChannelStore channelStore, LlmClient llm,
                       ImageGenerator imageGen, MusicGenerator musicGen,
                       TtsEngine ttsEngine, RenderEngine renderEngine,
                       BudgetGuard budgetGuard, CostTracker costTracker,
                       IdeaGenerator ideaGenerator) {
        this.jobStore = jobStore;
        this.channelStore = channelStore;
        this.llm = llm;
        this.imageGen = imageGen;
        this.musicGen = musicGen;
        this.ttsEngine = ttsEngine;
        this.renderEngine = renderEngine;
        this.budgetGuard = budgetGuard;
        this.costTracker = costTracker;
        this.ideaGenerator = ideaGenerator;
    }

    public Job run(String channelId) {
        ChannelProfile profile = channelStore.load(channelId);
        assertBudget(profile);
        Job job = Job.create(channelId);
        return execute(job, profile);
    }

    /**
     * Resumes an interrupted/failed job: finished steps (story, existing
     * scene images, music, completed language variants) are skipped, only
     * missing work is redone.
     */
    public Job resume(String jobId) {
        Job job = jobStore.load(jobId);
        if (job.getStatus() == JobStatus.PENDING_REVIEW
                || job.getStatus() == JobStatus.PUBLISHED
                || job.getStatus() == JobStatus.APPROVED) {
            logger.info("Job {} already complete ({}), nothing to resume",
                    jobId, job.getStatus());
            return job;
        }
        ChannelProfile profile = channelStore.load(job.getChannelId());
        assertBudget(profile);
        return execute(job, profile);
    }

    private void assertBudget(ChannelProfile profile) {
        // Budget check BEFORE any paid call
        double estimate = profile.getSceneCount() * Constants.COST_IMAGE_MEDIUM
                + profile.getLanguages().size() * Constants.COST_TTS_PER_1K_CHARS
                + Constants.COST_MUSIC_TRACK
                + (1 + profile.getLanguages().size()) * Constants.COST_LLM_CALL;
        budgetGuard.assertAllows(estimate);
    }

    private Job execute(Job job, ChannelProfile profile) {
        Path dir = jobStore.dirFor(job.getJobId());
        try {
            jobStore.save(job);

            // ===== PHASE 1: language-independent =====
            Story story = job.getStory();
            if (story == null) {
                NicheData niche = new NicheData(profile.getNiche().getTopic(),
                        profile.getNiche().getKeywords());
                List<ContentIdea> ideas = ideaGenerator.generateIdeas(niche, 5);
                ContentIdea idea = ideaGenerator.selectBestIdea(ideas);
                logger.info("Selected idea: {}", idea.getTitle());

                story = new StoryWriter(llm).write(idea, profile);
                job.setStory(story);
                job.getCost().setLlm(job.getCost().getLlm() + Constants.COST_LLM_CALL);
                jobStore.save(job);
            } else {
                logger.info("Resume: story already present, skipping idea/story generation");
            }

            double imageCost = new SceneImageService(imageGen)
                    .generateAll(story, dir.resolve("scenes"));
            job.getCost().setImages(job.getCost().getImages() + imageCost);
            jobStore.save(job);

            Path musicPath = dir.resolve("audio/music.mp3");
            if (!Files.exists(musicPath)) {
                Files.createDirectories(musicPath.getParent());
                musicGen.generate(
                        "tense ambient background, instrumental, cinematic, for: "
                                + story.getTitle(),
                        profile.getTargetDurationSeconds(), musicPath);
                job.getCost().setMusic(Constants.COST_MUSIC_TRACK);
            }
            job.setMusicFile("audio/music.mp3");
            job.setStatus(JobStatus.RENDERING);
            jobStore.save(job);

            // ===== PHASE 2: per language =====
            TranslationService translator = new TranslationService(llm);
            for (String lang : profile.getLanguages()) {
                // The recorded variant is the completion marker, NOT the mp4:
                // a crash between render and variant-save must reprocess.
                if (job.getVariants().stream().anyMatch(v -> lang.equals(v.getLang()))) {
                    logger.info("Skip completed variant {}", lang);
                    continue;
                }
                Path renderOut = dir.resolve("renders/" + lang + ".mp4");
                LocalizedStory localized = translator.localize(story, lang);
                job.getCost().setLlm(job.getCost().getLlm() + Constants.COST_LLM_CALL);

                String text = SceneTimer.joinNarrations(localized.getNarrations());
                Path audioOut = dir.resolve("audio/" + lang + ".mp3");
                Path alignOut = dir.resolve("audio/" + lang + ".alignment.json");
                ElevenLabsClient.TtsResult tts = ttsEngine.speak(
                        text, profile.getVoiceId(), audioOut, alignOut);
                job.getCost().setTts(job.getCost().getTts()
                        + text.length() / 1000.0 * Constants.COST_TTS_PER_1K_CHARS);
                jobStore.save(job);

                double[] ends = SceneTimer.sceneEndTimes(
                        localized.getNarrations(), tts.alignment());
                double[] durations = SceneTimer.sceneDurations(ends);
                List<SubtitleCue> cues = SubtitleRenderer.buildCues(tts.alignment(), 3);
                File ass = SubtitleRenderer.write(cues, dir.resolve("subs/" + lang + ".ass"));

                List<File> images = story.getScenes().stream()
                        .map(s -> dir.resolve(s.getImageFile()).toFile())
                        .toList();
                Files.createDirectories(renderOut.getParent());
                renderEngine.render(images, durations, tts.audioFile(),
                        musicPath.toFile(), ass, renderOut);

                LangVariant variant = new LangVariant();
                variant.setLang(lang);
                variant.setMetadata(localized.getMetadata());
                variant.setAudioFile("audio/" + lang + ".mp3");
                variant.setAlignmentFile("audio/" + lang + ".alignment.json");
                variant.setRenderFile("renders/" + lang + ".mp4");
                variant.setDurationSeconds(tts.alignment().totalDuration());
                variant.setPublications(new ArrayList<>());
                job.getVariants().add(variant);
                jobStore.save(job);
                logger.info("Variant ready: {} ({}s)", lang,
                        variant.getDurationSeconds());
            }

            if (!job.isCostRecorded()) {
                costTracker.add(job.getCost().total());
                job.setCostRecorded(true);
            }
            job.setStatus(JobStatus.PENDING_REVIEW);
            jobStore.save(job);
            logger.info("Job {} ready for review ({} variants, ${})",
                    job.getJobId(), job.getVariants().size(),
                    String.format("%.2f", job.getCost().total()));
            return job;

        } catch (Exception e) {
            job.setStatus(JobStatus.FAILED);
            job.setError(e.getMessage());
            try {
                jobStore.save(job);
            } catch (RuntimeException saveFailure) {
                // Never mask the original failure with a save failure
                logger.error("Could not persist FAILED state for {}",
                        job.getJobId(), saveFailure);
            }
            logger.error("Job {} failed", job.getJobId(), e);
            throw (e instanceof RuntimeException re) ? re : new RuntimeException(e);
        }
    }
}
