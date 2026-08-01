package com.videogenerator.api;

import com.videogenerator.model.Alignment;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class AlignmentParseTest {
    @Test
    void parsesSnakeCaseFieldsAndComputesDuration() throws Exception {
        String json = Files.readString(Path.of("src/test/resources/fixtures/alignment-sample.json"));
        Alignment a = ElevenLabsClient.parseAlignment(json);
        assertEquals(8, a.length());
        assertEquals(0.30, a.endOf(3), 1e-9);
        assertEquals(0.80, a.totalDuration(), 1e-9);
    }

    @Test
    void emptyAlignmentHasZeroDuration() {
        Alignment a = new Alignment();
        a.setCharacters(java.util.List.of());
        a.setCharacterStartTimesSeconds(java.util.List.of());
        a.setCharacterEndTimesSeconds(java.util.List.of());
        assertEquals(0, a.length());
        assertEquals(0.0, a.totalDuration(), 1e-9);
    }
}
