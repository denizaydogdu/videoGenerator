package com.videogenerator.velzon;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class VelzonBist100SymbolPoolTest {

    @Test
    void poolIsNonEmptyAndHasNoDuplicates() {
        List<String> symbols = VelzonBist100SymbolPool.SYMBOLS;
        assertFalse(symbols.isEmpty());
        Set<String> unique = new HashSet<>(symbols);
        assertEquals(symbols.size(), unique.size(), "sembol havuzunda tekrar olmamalı");
    }

    @Test
    void poolContainsKnownSymbols() {
        List<String> symbols = VelzonBist100SymbolPool.SYMBOLS;
        assertTrue(symbols.contains("THYAO"));
        assertTrue(symbols.contains("AKBNK"));
        assertTrue(symbols.contains("GARAN"));
        assertTrue(symbols.contains("ASELS"));
    }

    @Test
    void allSymbolsAreUppercaseLettersOnly() {
        for (String s : VelzonBist100SymbolPool.SYMBOLS) {
            assertTrue(s.matches("[A-Z]+"), "geçersiz sembol formatı: " + s);
        }
    }

    @Test
    void pickRandomReturnsRequestedCountOfDistinctSymbols() {
        List<String> picked = VelzonBist100SymbolPool.pickRandom(5);
        assertEquals(5, picked.size());
        assertEquals(5, new HashSet<>(picked).size(), "rastgele seçimde tekrar olmamalı");
        for (String s : picked) {
            assertTrue(VelzonBist100SymbolPool.SYMBOLS.contains(s));
        }
    }

    @Test
    void pickRandomRejectsCountLargerThanPool() {
        int tooMany = VelzonBist100SymbolPool.SYMBOLS.size() + 1;
        assertThrows(IllegalArgumentException.class,
                () -> VelzonBist100SymbolPool.pickRandom(tooMany));
    }

    @Test
    void pickRandomRejectsNonPositiveCount() {
        assertThrows(IllegalArgumentException.class, () -> VelzonBist100SymbolPool.pickRandom(0));
        assertThrows(IllegalArgumentException.class, () -> VelzonBist100SymbolPool.pickRandom(-1));
    }
}
