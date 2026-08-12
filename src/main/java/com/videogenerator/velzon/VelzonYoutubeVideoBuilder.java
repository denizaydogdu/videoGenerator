package com.videogenerator.velzon;

import com.videogenerator.api.ElevenLabsClient;
import com.videogenerator.api.ImageGenerator;
import com.videogenerator.model.VoiceConfig;
import com.videogenerator.processor.FFmpegWrapper;
import com.videogenerator.processor.KenBurnsRenderer;
import com.videogenerator.processor.SubtitleRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Gerçek video inşası: tek arkaplan görseli (AI üretimi) + ElevenLabs
 * seslendirme + KenBurnsRenderer TEK SAHNE modunda render. Bu sınıf, bir
 * önceki denemenin ürettiği ara dosyaları (görsel/ses/video) tekrar
 * üretmez — her adım kendi çıktı dosyasının VARLIĞINA bakar, yalnızca
 * eksikse üretir. Bu sayede yarım kalmış bir "Yayınla" denemesi (ör. TTS
 * başarılı ama render sırasında ffmpeg çökmüş) tekrar denendiğinde baştan
 * başlamaz.
 *
 * Altyazı YOK: bu, çok-sahneli/altyazılı pipeline'ın aksine tek bir statik
 * görsel üzerinde sesli anlatımdır. KenBurnsRenderer yine de bir .ass yolu
 * ZORUNLU kılıyor — SubtitleRenderer.write(List.of(), path) ile geçerli
 * başlık/bölümlere sahip ama sıfır Dialogue satırlı boş bir .ass dosyası
 * yazılır (bkz. alttaki AÇIK RİSK notu).
 *
 * AÇIK RİSK: Boş .ass dosyasının ffmpeg'in "subtitles=" filtresini
 * bozmadığı gerçek ffmpeg ile DOĞRULANMADI (bu ortamda ffmpeg çalıştırma
 * imkanı yoktu) — SubtitleRenderer'ın ürettiği [Script Info]/[V4+ Styles]/
 * [Events] iskeleti sözdizimsel olarak geçerli görünüyor (libass boş bir
 * [Events] bölümünü kabul etmeli) ama canlıda ilk kullanımda doğrulanmalı.
 */
public class VelzonYoutubeVideoBuilder implements VelzonYoutubePublishService.VideoBuilder {
    private static final Logger logger = LoggerFactory.getLogger(VelzonYoutubeVideoBuilder.class);

    private final ImageGenerator imageGen;
    private final ElevenLabsClient tts;
    private final FFmpegWrapper ffmpeg;
    private final KenBurnsRenderer renderer;
    private final VoiceConfig voiceConfig;

    public VelzonYoutubeVideoBuilder(ImageGenerator imageGen, ElevenLabsClient tts,
                                     FFmpegWrapper ffmpeg, KenBurnsRenderer renderer,
                                     VoiceConfig voiceConfig) {
        this.imageGen = imageGen;
        this.tts = tts;
        this.ffmpeg = ffmpeg;
        this.renderer = renderer;
        this.voiceConfig = voiceConfig;
    }

    private static String baseName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? fileName : fileName.substring(0, dot);
    }

    @Override
    public File build(Path batchDir, VelzonYoutubePublishService.ScriptEntry entry, int index)
            throws Exception {
        String base = baseName(entry.imageFile());
        Path imagePath = batchDir.resolve(entry.imageFile());
        Path audioPath = batchDir.resolve(base + ".mp3");
        Path videoPath = batchDir.resolve(base + ".mp4");

        if (Files.exists(videoPath)) {
            logger.info("Video already rendered, reusing: {}", videoPath.getFileName());
            return videoPath.toFile();
        }

        if (!Files.exists(imagePath)) {
            logger.info("Generating background image for {}", videoPath.getFileName());
            imageGen.generate(entry.imagePrompt(), imagePath);
        }
        if (!Files.exists(audioPath)) {
            logger.info("Generating voiceover for {}", videoPath.getFileName());
            tts.generateVoiceover(entry.narration(), voiceConfig, audioPath.toFile());
        }

        double durationSeconds = ffmpeg.getMediaDuration(audioPath.toString());

        Path assPath = Files.createTempFile(batchDir, "empty-", ".ass");
        try {
            SubtitleRenderer.write(List.of(), assPath); // geçerli başlık, sıfır Dialogue satırı
            renderer.render(List.of(imagePath.toFile()), new double[]{durationSeconds},
                    audioPath.toFile(), assPath.toFile(), videoPath);
        } finally {
            Files.deleteIfExists(assPath);
        }

        return videoPath.toFile();
    }

    @Override
    public void cleanup(Path batchDir, VelzonYoutubePublishService.ScriptEntry entry, int index)
            throws Exception {
        String base = baseName(entry.imageFile());
        Files.deleteIfExists(batchDir.resolve(entry.imageFile()));
        Files.deleteIfExists(batchDir.resolve(base + ".mp3"));
    }
}
