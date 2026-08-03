package dev.heitorrosa.packconverter.convert;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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
        assertTrue(detector.needsConversion(null, Collections.emptyList()));
    }

    @Test
    void blankMcmetaNeedsConversion() {
        assertTrue(detector.needsConversion("", Collections.emptyList()));
    }

    @Test
    void oldPackFormatOnlyNeedsConversion() {
        assertTrue(detector.needsConversion("{\"pack\":{\"pack_format\":2}}", Collections.emptyList()));
    }

    @Test
    void modernPackWithMinMaxDoesNotNeedConversion() {
        String modern = "{\"pack\":{\"min_format\":[88,0],\"max_format\":[88,0]}}";
        assertFalse(detector.needsConversion(modern, Collections.emptyList()));
    }

    @Test
    void legacyRootPathTriggersConversionEvenWithModernMcmeta() {
        String modern = "{\"pack\":{\"min_format\":[88,0],\"max_format\":[88,0]}}";
        List<String> paths = Arrays.asList("assets/minecraft/textures/blocks/planks_oak.png");
        assertTrue(detector.needsConversion(modern, paths));
    }

    @Test
    void modernMcmetaAndModernRootPathsDoNotNeedConversion() {
        String modern = "{\"pack\":{\"min_format\":[88,0],\"max_format\":[88,0]}}";
        List<String> paths = Arrays.asList("assets/minecraft/textures/block/oak_planks.png",
                "pack.mcmeta", "assets/minecraft/models/block/oak_planks.json");
        assertFalse(detector.needsConversion(modern, paths));
    }

    @Test
    void unparseableMcmetaNeedsConversion() {
        assertTrue(detector.needsConversion("{not json", Collections.emptyList()));
    }
}
