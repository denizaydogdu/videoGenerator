package com.videogenerator.job;

import com.videogenerator.api.ElevenLabsClient;
import com.videogenerator.api.ImageGenerator;
import com.videogenerator.api.LlmClient;
import com.videogenerator.api.MusicGenerator;
import com.videogenerator.channel.ChannelStore;
import com.videogenerator.channel.ChannelStoreTest;
import com.videogenerator.model.Alignment;
import com.videogenerator.model.ContentIdea;
import com.videogenerator.model.FakeAlignments;
import com.videogenerator.service.IdeaGenerator;
import com.videogenerator.service.StoryWriterTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class JobPipelineTest {
    static final String LOC_JSON = """
        {"narrations":["a.","b.","c."],
         "metadata":{"title":"T","description":"D","hashtags":["#x"]}}""";

    private Path channelsDir(Path root, String json) throws Exception {
        Path channels = root.resolve("channels");
        Files.createDirectories(channels);
        Files.writeString(channels.resolve("ch1.json"), json);
        return channels;
    }

    @Test
    void happyPathEndsPendingReviewWithCosts(@TempDir Path root) throws Exception {
        Path channels = channelsDir(root,
                ChannelStoreTest.VALID.replace("truecrime-en", "ch1")
                        .replace("\"sceneCount\":6", "\"sceneCount\":3")
                        .replace("[\"en\",\"es\"]", "[\"en\"]"));

        LlmClient llm = (sys, user) ->
                user.contains("Write a gripping") ? StoryWriterTest.LLM_JSON : LOC_JSON;
        ImageGenerator img = (prompt, out) -> {
            try {
                Files.writeString(out, "png");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return out.toFile();
        };
        MusicGenerator music = (prompt, dur, out) -> {
            try {
                Files.writeString(out, "mp3");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return out.toFile();
        };
        JobPipeline.TtsEngine tts = (text, voice, audioOut, alignOut) -> {
            try {
                Files.writeString(audioOut, "mp3");
                Alignment a = FakeAlignments.forText(text);
                Files.writeString(alignOut, new com.google.gson.Gson().toJson(a));
                return new ElevenLabsClient.TtsResult(audioOut.toFile(), a);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
        JobPipeline.RenderEngine render = (images, durations, voiceover, musicFile, ass, out) -> {
            Files.writeString(out, "mp4");
            return out.toFile();
        };
        IdeaGenerator ideas = mock(IdeaGenerator.class);
        ContentIdea idea = new ContentIdea();
        idea.setTitle("Vanished");
        when(ideas.generateIdeas(any(), anyInt())).thenReturn(List.of(idea));
        when(ideas.selectBestIdea(any())).thenReturn(idea);

        JobStore jobs = new JobStore(root.resolve("jobs"));
        CostTracker tracker = new CostTracker(root.resolve("costs"));
        JobPipeline pipeline = new JobPipeline(jobs, new ChannelStore(channels),
                llm, img, music, tts, render, new BudgetGuard(tracker, 100.0), tracker, ideas);

        Job job = pipeline.run("ch1");

        assertEquals(JobStatus.PENDING_REVIEW, job.getStatus());
        assertEquals(1, job.getVariants().size());
        assertTrue(Files.exists(jobs.dirFor(job.getJobId()).resolve("renders/en.mp4")));
        assertTrue(Files.exists(jobs.dirFor(job.getJobId()).resolve("scenes/01a.png")));
        assertTrue(Files.exists(jobs.dirFor(job.getJobId()).resolve("subs/en.ass")));
        assertTrue(job.getCost().total() > 0);
        assertEquals(job.getCost().total(), tracker.spentThisMonth(), 1e-9);
        assertTrue(job.getVariants().get(0).getDurationSeconds() > 0);
        assertEquals("T", job.getVariants().get(0).getMetadata().getTitle());
    }

    @Test
    void voiceOnlyModeSkipsMusicEntirely(@TempDir Path root) throws Exception {
        Path channels = channelsDir(root,
                ChannelStoreTest.VALID.replace("truecrime-en", "ch1")
                        .replace("\"sceneCount\":6", "\"sceneCount\":3")
                        .replace("[\"en\",\"es\"]", "[\"en\"]"));
        LlmClient llm = (sys, user) ->
                user.contains("Write a gripping") ? StoryWriterTest.LLM_JSON : LOC_JSON;
        ImageGenerator img = (p, out) -> {
            try { Files.writeString(out, "png"); } catch (Exception e) { throw new RuntimeException(e); }
            return out.toFile();
        };
        MusicGenerator music = (p, d, out) -> {
            throw new AssertionError("music must not be called in voice-only mode");
        };
        JobPipeline.TtsEngine tts = (text, voice, audioOut, alignOut) -> {
            try {
                Files.writeString(audioOut, "mp3");
                Alignment a = FakeAlignments.forText(text);
                Files.writeString(alignOut, new com.google.gson.Gson().toJson(a));
                return new ElevenLabsClient.TtsResult(audioOut.toFile(), a);
            } catch (Exception e) { throw new RuntimeException(e); }
        };
        JobPipeline.RenderEngine render = (images, durations, vo, mus, ass, out) -> {
            if (mus != null) throw new AssertionError("music file must be null");
            Files.writeString(out, "mp4");
            return out.toFile();
        };
        IdeaGenerator ideas = mock(IdeaGenerator.class);
        ContentIdea idea = new ContentIdea();
        idea.setTitle("Vanished");
        when(ideas.generateIdeas(any(), anyInt())).thenReturn(List.of(idea));
        when(ideas.selectBestIdea(any())).thenReturn(idea);

        JobStore jobs = new JobStore(root.resolve("jobs"));
        CostTracker tracker = new CostTracker(root.resolve("costs"));
        JobPipeline pipeline = new JobPipeline(jobs, new ChannelStore(channels),
                llm, img, music, tts, render, new BudgetGuard(tracker, 100.0),
                tracker, ideas, false); // music disabled

        Job job = pipeline.run("ch1");
        assertEquals(JobStatus.PENDING_REVIEW, job.getStatus());
        assertNull(job.getMusicFile());
        assertEquals(0.0, job.getCost().getMusic(), 1e-9);
    }

    @Test
    void topicHintBypassesIdeaGeneratorAndReachesPrompt(@TempDir Path root) throws Exception {
        Path channels = channelsDir(root,
                ChannelStoreTest.VALID.replace("truecrime-en", "ch1")
                        .replace("\"sceneCount\":6", "\"sceneCount\":3")
                        .replace("[\"en\",\"es\"]", "[\"en\"]"));
        List<String> prompts = new java.util.ArrayList<>();
        LlmClient llm = (sys, user) -> {
            prompts.add(user);
            return user.contains("Write a gripping") ? StoryWriterTest.LLM_JSON : LOC_JSON;
        };
        ImageGenerator img = (p, out) -> {
            try { Files.writeString(out, "png"); } catch (Exception e) { throw new RuntimeException(e); }
            return out.toFile();
        };
        JobPipeline.TtsEngine tts = (text, voice, audioOut, alignOut) -> {
            try {
                Files.writeString(audioOut, "mp3");
                Alignment a = FakeAlignments.forText(text);
                Files.writeString(alignOut, new com.google.gson.Gson().toJson(a));
                return new ElevenLabsClient.TtsResult(audioOut.toFile(), a);
            } catch (Exception e) { throw new RuntimeException(e); }
        };
        JobPipeline.RenderEngine render = (images, durations, vo, mus, ass, out) -> {
            Files.writeString(out, "mp4");
            return out.toFile();
        };
        IdeaGenerator ideas = mock(IdeaGenerator.class);

        JobStore jobs = new JobStore(root.resolve("jobs"));
        CostTracker tracker = new CostTracker(root.resolve("costs"));
        JobPipeline pipeline = new JobPipeline(jobs, new ChannelStore(channels),
                llm, img, (p, d, out) -> { throw new AssertionError("no music"); },
                tts, render, new BudgetGuard(tracker, 100.0), tracker, ideas, false)
                .withTopicHint("the 1993 Anamur lighthouse cold case");

        Job job = pipeline.run("ch1");

        assertEquals(JobStatus.PENDING_REVIEW, job.getStatus());
        verifyNoInteractions(ideas);
        assertTrue(prompts.stream().anyMatch(p ->
                        p.contains("Topic: the 1993 Anamur lighthouse cold case")),
                "hint must reach the story prompt as the topic");
    }

    @Test
    void resumeSkipsCompletedVariantsAndFinishes(@TempDir Path root) throws Exception {
        Path channels = channelsDir(root,
                ChannelStoreTest.VALID.replace("truecrime-en", "ch1")
                        .replace("\"sceneCount\":6", "\"sceneCount\":3"));
        // languages: ["en","es"] (VALID'in orijinali)

        LlmClient llm = (sys, user) ->
                user.contains("Write a gripping") ? StoryWriterTest.LLM_JSON : LOC_JSON;
        ImageGenerator img = (prompt, out) -> {
            try { Files.writeString(out, "png"); } catch (Exception e) { throw new RuntimeException(e); }
            return out.toFile();
        };
        MusicGenerator music = (prompt, dur, out) -> {
            try { Files.writeString(out, "mp3"); } catch (Exception e) { throw new RuntimeException(e); }
            return out.toFile();
        };
        JobPipeline.TtsEngine tts = (text, voice, audioOut, alignOut) -> {
            try {
                Files.writeString(audioOut, "mp3");
                Alignment a = FakeAlignments.forText(text);
                Files.writeString(alignOut, new com.google.gson.Gson().toJson(a));
                return new ElevenLabsClient.TtsResult(audioOut.toFile(), a);
            } catch (Exception e) { throw new RuntimeException(e); }
        };
        java.util.Map<String, Integer> renderCalls = new java.util.HashMap<>();
        JobPipeline.RenderEngine failingOnEs = (images, durations, vo, mus, ass, out) -> {
            String lang = out.getFileName().toString().replace(".mp4", "");
            renderCalls.merge(lang, 1, Integer::sum);
            if ("es".equals(lang)) {
                throw new RuntimeException("render crash");
            }
            Files.writeString(out, "mp4");
            return out.toFile();
        };
        IdeaGenerator ideas = mock(IdeaGenerator.class);
        ContentIdea idea = new ContentIdea();
        idea.setTitle("Vanished");
        when(ideas.generateIdeas(any(), anyInt())).thenReturn(List.of(idea));
        when(ideas.selectBestIdea(any())).thenReturn(idea);

        JobStore jobs = new JobStore(root.resolve("jobs"));
        CostTracker tracker = new CostTracker(root.resolve("costs"));
        JobPipeline broken = new JobPipeline(jobs, new ChannelStore(channels),
                llm, img, music, tts, failingOnEs, new BudgetGuard(tracker, 100.0), tracker, ideas);

        assertThrows(RuntimeException.class, () -> broken.run("ch1"));
        Job failed = jobs.list().get(0);
        assertEquals(JobStatus.FAILED, failed.getStatus());
        assertEquals(1, failed.getVariants().size()); // en tamam, es yok

        JobPipeline.RenderEngine working = (images, durations, vo, mus, ass, out) -> {
            String lang = out.getFileName().toString().replace(".mp4", "");
            renderCalls.merge(lang, 1, Integer::sum);
            Files.writeString(out, "mp4");
            return out.toFile();
        };
        JobPipeline fixed = new JobPipeline(jobs, new ChannelStore(channels),
                llm, img, music, tts, working, new BudgetGuard(tracker, 100.0), tracker, ideas);

        Job resumed = fixed.resume(failed.getJobId());

        assertEquals(JobStatus.PENDING_REVIEW, resumed.getStatus());
        assertNull(resumed.getError(), "başarılı koşu bayat error alanını temizlemeli");
        assertEquals(2, resumed.getVariants().size());
        assertEquals(1, renderCalls.get("en")); // en YENİDEN render edilmedi
        assertEquals(2, renderCalls.get("es")); // 1 çöken + 1 başarılı
    }

    @Test
    void budgetBlockPreventsAnyApiCall(@TempDir Path root) throws Exception {
        Path channels = channelsDir(root, ChannelStoreTest.VALID.replace("truecrime-en", "ch1"));
        CostTracker tracker = new CostTracker(root.resolve("costs"));
        tracker.add(100.0); // bütçe dolu
        LlmClient llm = mock(LlmClient.class);
        IdeaGenerator ideas = mock(IdeaGenerator.class);
        JobPipeline pipeline = new JobPipeline(new JobStore(root.resolve("jobs")),
                new ChannelStore(channels), llm, null, null, null, null,
                new BudgetGuard(tracker, 100.0), tracker, ideas);

        assertThrows(IllegalStateException.class, () -> pipeline.run("ch1"));
        verifyNoInteractions(llm);
        verifyNoInteractions(ideas);
    }

    @Test
    void failureMarksJobFailedWithError(@TempDir Path root) throws Exception {
        Path channels = channelsDir(root,
                ChannelStoreTest.VALID.replace("truecrime-en", "ch1")
                        .replace("\"sceneCount\":6", "\"sceneCount\":3")
                        .replace("[\"en\",\"es\"]", "[\"en\"]"));
        LlmClient llm = (sys, user) -> {
            throw new RuntimeException("LLM down");
        };
        IdeaGenerator ideas = mock(IdeaGenerator.class);
        ContentIdea idea = new ContentIdea();
        idea.setTitle("X");
        when(ideas.generateIdeas(any(), anyInt())).thenReturn(List.of(idea));
        when(ideas.selectBestIdea(any())).thenReturn(idea);

        JobStore jobs = new JobStore(root.resolve("jobs"));
        CostTracker tracker = new CostTracker(root.resolve("costs"));
        JobPipeline pipeline = new JobPipeline(jobs, new ChannelStore(channels),
                llm, null, null, null, null,
                new BudgetGuard(tracker, 100.0), tracker, ideas);

        assertThrows(RuntimeException.class, () -> pipeline.run("ch1"));
        Job failed = jobs.list().get(0);
        assertEquals(JobStatus.FAILED, failed.getStatus());
        assertNotNull(failed.getError());
    }
}
