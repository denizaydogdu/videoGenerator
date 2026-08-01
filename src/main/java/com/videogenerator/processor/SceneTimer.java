package com.videogenerator.processor;

import com.videogenerator.model.Alignment;

import java.util.List;

/**
 * Derives scene cut points from TTS alignment. The narration is spoken as
 * one concatenated text (see {@link #joinNarrations}); each scene's end is
 * the end time of its last character. Image durations AND subtitle timing
 * both derive from the same alignment, so they cannot drift apart.
 */
public final class SceneTimer {
    private SceneTimer() {
    }

    /**
     * The EXACT text sent to TTS. Cut computation depends on this joining;
     * never build the TTS input any other way.
     */
    public static String joinNarrations(List<String> narrations) {
        return String.join(" ", narrations);
    }

    /**
     * @return per-scene end times in seconds; the last scene always extends
     *         to the full audio duration
     */
    public static double[] sceneEndTimes(List<String> narrations, Alignment a) {
        double[] ends = new double[narrations.size()];
        int cursor = -1; // index of last processed char in the joined text
        for (int i = 0; i < narrations.size(); i++) {
            if (i > 0) {
                cursor += 1; // separator space
            }
            cursor += narrations.get(i).length();
            int idx = Math.min(cursor, a.length() - 1); // tolerate normalization drift
            ends[i] = a.endOf(idx);
        }
        ends[narrations.size() - 1] = a.totalDuration();
        return ends;
    }

    /**
     * @return per-scene durations (consecutive differences of end times)
     */
    public static double[] sceneDurations(double[] endTimes) {
        double[] durations = new double[endTimes.length];
        double prev = 0;
        for (int i = 0; i < endTimes.length; i++) {
            durations[i] = endTimes[i] - prev;
            prev = endTimes[i];
        }
        return durations;
    }
}
