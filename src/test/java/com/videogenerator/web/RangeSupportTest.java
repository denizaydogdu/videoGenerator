package com.videogenerator.web;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RangeSupportTest {
    @Test
    void parsesStandardForms() {
        assertEquals(new RangeSupport.ByteRange(0, 499),
                RangeSupport.parse("bytes=0-499", 1000));
        assertEquals(new RangeSupport.ByteRange(500, 999),
                RangeSupport.parse("bytes=500-", 1000));
        assertEquals(new RangeSupport.ByteRange(500, 999),
                RangeSupport.parse("bytes=-500", 1000)); // son 500 bayt
    }

    @Test
    void clampsEndToFileLength() {
        assertEquals(new RangeSupport.ByteRange(0, 999),
                RangeSupport.parse("bytes=0-99999", 1000));
    }

    @Test
    void invalidOrUnsatisfiableReturnsNull() {
        assertNull(RangeSupport.parse(null, 1000));
        assertNull(RangeSupport.parse("chunks=0-1", 1000));
        assertNull(RangeSupport.parse("bytes=abc-def", 1000));
        assertNull(RangeSupport.parse("bytes=1000-", 1000)); // dosya dışı
        assertNull(RangeSupport.parse("bytes=500-100", 1000)); // ters
        assertNull(RangeSupport.parse("bytes=-0", 1000));
    }

    @Test
    void overflowingPositionsClampPerRfc9110() {
        assertEquals(new RangeSupport.ByteRange(0, 999),
                RangeSupport.parse("bytes=0-9223372036854775808", 1000));
        assertEquals(new RangeSupport.ByteRange(0, 999),
                RangeSupport.parse("bytes=-9223372036854775808", 1000));
    }

    @Test
    void multiRangeTakesFirst() {
        assertEquals(new RangeSupport.ByteRange(0, 99),
                RangeSupport.parse("bytes=0-99,200-299", 1000));
    }
}
