package dev.heitorrosa.packconverter.convert;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link OldPackDetector}: decides whether a pack needs conversion.
 */
class OldPackDetectorTest {

    private static OldPackDetector detector;

    @BeforeAll
    static void setUp() {
        detector = new OldPackDetector();
    }

    @Test
    void nullMcmetaNeedsConversion() {
        assertTrue(detector.needsConversion(null));
    }

    @Test
    void blankMcmetaNeedsConversion() {
        assertTrue(detector.needsConversion(""));
    }

    @Test
    void oldPackFormatOnlyNeedsConversion() {
        assertTrue(detector.needsConversion("{\"pack\":{\"pack_format\":2}}"));
    }

    @Test
    void modernPackWithMinMaxDoesNotNeedConversion() {
        String modern = "{\"pack\":{\"min_format\":[88,0],\"max_format\":[88,0]}}";
        assertFalse(detector.needsConversion(modern));
    }

    @Test
    void unparseableMcmetaNeedsConversion() {
        assertTrue(detector.needsConversion("{not json"));
    }
}
