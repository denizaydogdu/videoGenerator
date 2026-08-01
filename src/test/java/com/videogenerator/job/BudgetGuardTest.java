package com.videogenerator.job;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class BudgetGuardTest {
    @Test
    void accumulatesAndBlocksOverBudget(@TempDir Path dir) {
        CostTracker tracker = new CostTracker(dir);
        tracker.add(40.0);
        tracker.add(20.0);
        assertEquals(60.0, tracker.spentThisMonth(), 1e-9);

        BudgetGuard guard = new BudgetGuard(tracker, 100.0);
        assertDoesNotThrow(() -> guard.assertAllows(39.0));
        assertThrows(IllegalStateException.class, () -> guard.assertAllows(41.0));
    }

    @Test
    void persistsAcrossInstances(@TempDir Path dir) {
        new CostTracker(dir).add(5.0);
        assertEquals(5.0, new CostTracker(dir).spentThisMonth(), 1e-9);
    }

    @Test
    void corruptCostFileFailsClosed(@TempDir Path dir) throws Exception {
        CostTracker tracker = new CostTracker(dir);
        tracker.add(1.0);
        // Ay dosyasını boz: bütçe kontrolü 0 görmemeli, patlamalı
        try (var files = java.nio.file.Files.list(dir)) {
            java.nio.file.Path json = files
                .filter(f -> f.toString().endsWith(".json")).findFirst().orElseThrow();
            java.nio.file.Files.writeString(json, "{not valid json");
        }
        assertThrows(IllegalStateException.class, tracker::spentThisMonth);
    }
}
