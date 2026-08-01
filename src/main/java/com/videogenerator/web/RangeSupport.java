package com.videogenerator.web;

/**
 * HTTP Range header parsing for video seeking. JDK HttpServer has no
 * built-in support; without 206 responses &lt;video&gt; cannot seek.
 */
public final class RangeSupport {
    private RangeSupport() {
    }

    /** Inclusive byte range. */
    public record ByteRange(long start, long end) {
        public long length() {
            return end - start + 1;
        }
    }

    /**
     * Parses a Range header. Multi-range requests use only the first range.
     *
     * @return the satisfiable range, or null when the header is absent,
     *         malformed or unsatisfiable (caller then sends 200-full or 416)
     */
    public static ByteRange parse(String header, long fileLength) {
        if (header == null || !header.startsWith("bytes=") || fileLength <= 0) {
            return null;
        }
        String spec = header.substring("bytes=".length());
        int comma = spec.indexOf(',');
        if (comma >= 0) {
            spec = spec.substring(0, comma); // ilk range
        }
        int dash = spec.indexOf('-');
        if (dash < 0) {
            return null;
        }
        String startPart = spec.substring(0, dash).trim();
        String endPart = spec.substring(dash + 1).trim();
        if (startPart.isEmpty()) {
            // bytes=-N → son N bayt; RFC 9110: uzunluğu aşan suffix = tamamı
            Long suffix = parseClamped(endPart);
            if (suffix == null || suffix <= 0) {
                return null;
            }
            long start = Math.max(0, fileLength - suffix);
            return new ByteRange(start, fileLength - 1);
        }
        Long start = parseClamped(startPart);
        if (start == null || start >= fileLength) {
            return null;
        }
        long end;
        if (endPart.isEmpty()) {
            end = fileLength - 1;
        } else {
            Long parsedEnd = parseClamped(endPart);
            if (parsedEnd == null) {
                return null;
            }
            // RFC 9110: dosya boyunu aşan end = son bayt
            end = Math.min(parsedEnd, fileLength - 1);
        }
        if (end < start) {
            return null;
        }
        return new ByteRange(start, end);
    }

    /**
     * Parses a non-negative decimal; digits-only values that overflow long
     * clamp to Long.MAX_VALUE (RFC treats oversized positions as "to end").
     * Non-numeric input returns null.
     */
    private static Long parseClamped(String s) {
        if (s.isEmpty() || !s.chars().allMatch(Character::isDigit)) {
            return null;
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException overflow) {
            return Long.MAX_VALUE;
        }
    }
}
