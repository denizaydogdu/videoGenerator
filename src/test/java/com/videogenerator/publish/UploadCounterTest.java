package com.videogenerator.publish;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class UploadCounterTest {
    @Test
    void countsAndPersistsPerDay(@TempDir Path dir) {
        UploadCounter counter = new UploadCounter(dir);
        assertEquals(0, counter.today());
        counter.increment();
        counter.increment();
        assertEquals(2, counter.today());
        assertEquals(2, new UploadCounter(dir).today(), "kalıcı olmalı");
    }
}
