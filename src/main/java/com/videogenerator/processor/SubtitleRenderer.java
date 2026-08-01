package com.videogenerator.processor;

import com.videogenerator.model.Alignment;
import com.videogenerator.model.SubtitleCue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Builds burn-in subtitles from TTS alignment: characters are grouped into
 * words, words into cues, cues into an ASS file with a bottom-safe-zone
 * margin (players/UI overlays cover the lowest ~390px on Shorts).
 */
public final class SubtitleRenderer {
    private SubtitleRenderer() {
    }

    public static List<SubtitleCue> buildCues(Alignment a, int maxWordsPerCue) {
        record Word(int startIdx, int endIdx) {
        }
        List<Word> words = new ArrayList<>();
        int wordStart = -1;
        for (int i = 0; i < a.length(); i++) {
            boolean space = a.getCharacters().get(i).isBlank();
            if (!space && wordStart < 0) {
                wordStart = i;
            }
            if ((space || i == a.length() - 1) && wordStart >= 0) {
                words.add(new Word(wordStart, space ? i - 1 : i));
                wordStart = -1;
            }
        }
        List<SubtitleCue> cues = new ArrayList<>();
        for (int w = 0; w < words.size(); w += maxWordsPerCue) {
            int last = Math.min(w + maxWordsPerCue, words.size()) - 1;
            StringBuilder text = new StringBuilder();
            for (int k = w; k <= last; k++) {
                if (k > w) {
                    text.append(' ');
                }
                for (int c = words.get(k).startIdx(); c <= words.get(k).endIdx(); c++) {
                    text.append(a.getCharacters().get(c));
                }
            }
            SubtitleCue cue = new SubtitleCue();
            cue.setStart(a.getCharacterStartTimesSeconds().get(words.get(w).startIdx()));
            cue.setEnd(a.getCharacterEndTimesSeconds().get(words.get(last).endIdx()));
            cue.setText(text.toString());
            cues.add(cue);
        }
        return cues;
    }

    public static String toAss(List<SubtitleCue> cues) {
        StringBuilder sb = new StringBuilder("""
                [Script Info]
                ScriptType: v4.00+
                PlayResX: 1080
                PlayResY: 1920

                [V4+ Styles]
                Format: Name, Fontname, Fontsize, PrimaryColour, OutlineColour, BackColour, Bold, Outline, Shadow, Alignment, MarginL, MarginR, MarginV
                Style: Default,Arial,72,&H00FFFFFF,&H00000000,&H80000000,-1,4,0,2,60,60,420

                [Events]
                Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
                """);
        for (SubtitleCue c : cues) {
            sb.append(String.format(Locale.ROOT, "Dialogue: 0,%s,%s,Default,,0,0,0,,%s%n",
                    assTime(c.getStart()), assTime(c.getEnd()), escapeAssText(c.getText())));
        }
        return sb.toString();
    }

    /**
     * ASS uses H:MM:SS.cc (centiseconds). Rounds to centiseconds FIRST and
     * then splits, so 59.995s becomes 0:01:00.00 instead of invalid 0:00:60.00.
     */
    static String assTime(double seconds) {
        long totalCs = Math.round(seconds * 100.0);
        long h = totalCs / 360000;
        long m = (totalCs % 360000) / 6000;
        long s = (totalCs % 6000) / 100;
        long cs = totalCs % 100;
        return String.format(Locale.ROOT, "%d:%02d:%02d.%02d", h, m, s, cs);
    }

    /**
     * Newlines break the Dialogue line format; braces start override blocks;
     * backslashes start control sequences (\N, \h) in libass.
     */
    static String escapeAssText(String text) {
        return text.replace("\n", " ").replace("\r", " ")
                .replace("\\", "/")
                .replace("{", "(").replace("}", ")");
    }

    public static File write(List<SubtitleCue> cues, Path out) throws IOException {
        Files.createDirectories(out.getParent());
        Files.writeString(out, toAss(cues));
        return out.toFile();
    }
}
