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
    private final boolean musicEnabled;
    private Path localMusicDir; // F5: telifsiz yerel müzik klasörü (opsiyonel)
    private String topicHint;   // vaka rotasyonu: işe özel konu (opsiyonel)

    /** F4b — dil başına son-kart seri vaadi (görsel CTA; sesli CTA yok). */
    private static final java.util.Map<String, String> END_CARDS = java.util.Map.of(
            "tr", "YARIN YENİ DOSYA →",
            "en", "NEW CASE TOMORROW →",
            "es", "NUEVO CASO MAÑANA →");

    public JobPipeline withLocalMusicDir(Path dir) {
        this.localMusicDir = dir;
        return this;
    }

    /**
     * Vaka rotasyonu için işe özel konu ipucu (ör. "a well-documented Turkish
     * cold case, unsolved 10+ years"). Verilirse IdeaGenerator atlanır ve
     * hikâye doğrudan bu konuyla yazılır.
     */
    public JobPipeline withTopicHint(String hint) {
        this.topicHint = (hint == null || hint.isBlank()) ? null : hint.trim();
        return this;
    }

    public JobPipeline(JobStore jobStore, ChannelStore channelStore, LlmClient llm,
                       ImageGenerator imageGen, MusicGenerator musicGen,
                       TtsEngine ttsEngine, RenderEngine renderEngine,
                       BudgetGuard budgetGuard, CostTracker costTracker,
                       IdeaGenerator ideaGenerator) {
        this(jobStore, channelStore, llm, imageGen, musicGen, ttsEngine,
                renderEngine, budgetGuard, costTracker, ideaGenerator, true);
    }

    /**
     * @param musicEnabled false = voice-only renders (e.g. ElevenLabs free
     *                     plan has no Music API access)
     */
    public JobPipeline(JobStore jobStore, ChannelStore channelStore, LlmClient llm,
                       ImageGenerator imageGen, MusicGenerator musicGen,
                       TtsEngine ttsEngine, RenderEngine renderEngine,
                       BudgetGuard budgetGuard, CostTracker costTracker,
                       IdeaGenerator ideaGenerator, boolean musicEnabled) {
        this.musicEnabled = musicEnabled;
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
                ContentIdea idea;
                if (topicHint != null) {
                    idea = new ContentIdea(topicHint, topicHint);
                    logger.info("Topic hint given, skipping idea generation: {}", topicHint);
                } else {
                    NicheData niche = new NicheData(profile.getNiche().getTopic(),
                            profile.getNiche().getKeywords());
                    List<ContentIdea> ideas = ideaGenerator.generateIdeas(niche, 5);
                    idea = ideaGenerator.selectBestIdea(ideas);
                    logger.info("Selected idea: {}", idea.getTitle());
                }

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
            if (musicEnabled) {
                if (!Files.exists(musicPath)) {
                    Files.createDirectories(musicPath.getParent());
                    musicGen.generate(
                            "tense ambient background, instrumental, cinematic, for: "
                                    + story.getTitle(),
                            profile.getTargetDurationSeconds(), musicPath);
                    job.getCost().setMusic(Constants.COST_MUSIC_TRACK);
                }
                job.setMusicFile("audio/music.mp3");
            } else {
                // F5: API müziği kapalı — yerel telifsiz kütüphaneden dene
                Path local = LocalMusicLibrary.pickFor(job.getJobId(), localMusicDir);
                if (local != null && !Files.exists(musicPath)) {
                    Files.createDirectories(musicPath.getParent());
                    Files.copy(local, musicPath);
                    job.setMusicFile("audio/music.mp3");
                    logger.info("Local music bed: {}", local.getFileName());
                } else if (Files.exists(musicPath)) {
                    job.setMusicFile("audio/music.mp3");
                } else {
                    logger.info("music disabled & no local library — voice-only");
                    job.setMusicFile(null);
                }
            }
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
                Files.createDirectories(audioOut.getParent()); // voice-only modda da garanti
                ElevenLabsClient.TtsResult tts = ttsEngine.speak(
                        text, profile.getVoiceId(), audioOut, alignOut);
                job.getCost().setTts(job.getCost().getTts()
                        + text.length() / 1000.0 * Constants.COST_TTS_PER_1K_CHARS);
                jobStore.save(job);

                double[] ends = SceneTimer.sceneEndTimes(
                        localized.getNarrations(), tts.alignment());
                double[] sceneDurations = SceneTimer.sceneDurations(ends);
                // F3+F4b: karaoke stil + son-kart seri vaadi
                File ass = SubtitleRenderer.writeKaraoke(tts.alignment(), 3,
                        localized.getHookText(),
                        END_CARDS.getOrDefault(lang, END_CARDS.get("en")),
                        dir.resolve("subs/" + lang + ".ass"));

                // F2: sahne süresi o sahnenin görselleri arasında eşit bölünür
                // → her 3-6 saniyede görüntü değişimi (tutma sinyali)
                List<File> images = new ArrayList<>();
                List<Double> perImage = new ArrayList<>();
                for (int s = 0; s < story.getScenes().size(); s++) {
                    List<String> sceneFiles = story.getScenes().get(s).getImageFiles();
                    for (String f : sceneFiles) {
                        images.add(dir.resolve(f).toFile());
                        perImage.add(sceneDurations[s] / sceneFiles.size());
                    }
                }
                double[] durations = perImage.stream()
                        .mapToDouble(Double::doubleValue).toArray();
                if (images.isEmpty()) {
                    throw new IllegalStateException(
                            "No scene images resolved for render (job "
                                    + job.getJobId() + ")");
                }
                Files.createDirectories(renderOut.getParent());
                renderEngine.render(images, durations, tts.audioFile(),
                        job.getMusicFile() != null ? musicPath.toFile() : null,
                        ass, renderOut);

                LangVariant variant = new LangVariant();
                variant.setLang(lang);
                localized.getMetadata().setLanguage(lang); // YouTube dil hedeflemesi
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
            job.setError(null); // önceki başarısız denemenin bayat hatası kalmasın
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
