package dev.heitorrosa.packconverter.convert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link SliceEngine}: request-time slicing with a fake source
 * reader, empty/missing-source fallback, directory surfacing, and the
 * built-in explosion splice (16 cells of a 4x4 grid).
 */
class SliceEngineTest {

    private static final byte[] ATLAS = PngTestUtil.png(256, 256);
    private static final byte[] EXPLOSION = PngTestUtil.png(128, 128);

    private final SliceEngine engine = new SliceEngine(loaderWithHotbar(), new Slicer());

    private static SliceSpecLoader loaderWithHotbar() {
        return new SliceSpecLoader(List.of(new SliceSpec("textures/gui/widgets.png",
                List.of(new SliceTarget("textures/gui/sprites/hud/hotbar.png", 0, 0, 182, 22)))));
    }

    @Test
    void trySliceServesCorrectBoxFromReader() {
        byte[] sliced = engine.trySlice("textures/gui/sprites/hud/hotbar.png", p -> ATLAS);

        assertNotNull(sliced);
        assertEquals(182, PngTestUtil.width(sliced));
        assertEquals(22, PngTestUtil.height(sliced));
        // Box starts at (0,0): slice pixel (7, 5) == source pixel (7, 5).
        assertEquals(PngTestUtil.rgb(ATLAS, 7, 5), PngTestUtil.rgb(sliced, 7, 5));
    }

    @Test
    void trySliceUnknownTargetReturnsNull() {
        assertNull(engine.trySlice("textures/gui/sprites/hud/nope.png", p -> ATLAS));
    }

    @Test
    void trySliceMissingSourceReturnsNull() {
        assertNull(engine.trySlice("textures/gui/sprites/hud/hotbar.png", p -> null));
        assertNull(engine.trySlice("textures/gui/sprites/hud/hotbar.png", null));
    }

    @Test
    void trySliceEmptySourceReturnsNull() {
        assertNull(engine.trySlice("textures/gui/sprites/hud/hotbar.png",
                p -> PngTestUtil.transparentPng(256, 256)));
    }

    @Test
    void sliceTargetsUnderListsOnlyExistingSources() {
        List<String> withSource = engine.sliceTargetsUnder("textures/gui/sprites/hud", p -> true);
        assertEquals(List.of("textures/gui/sprites/hud/hotbar.png"), withSource);

        List<String> withoutSource = engine.sliceTargetsUnder("textures/gui/sprites/hud", p -> false);
        assertTrue(withoutSource.isEmpty());
    }

    @Test
    void sliceTargetsUnderHonorsDirectoryBoundary() {
        // Sibling-ish directory (no trailing slash boundary match) must not match.
        assertTrue(engine.sliceTargetsUnder("textures/gui/sprites/hudx", p -> true).isEmpty());
    }

    @Test
    void explosionSplicesSixteenCellsRowMajor() {
        // explosion_0 = row 0 col 0 -> box (0,0); explosion_5 = row 1 col 1 -> (32,32);
        // explosion_15 = row 3 col 3 -> (96,96).
        byte[] cell0 = engine.trySlice("textures/particle/explosion_0.png", p -> EXPLOSION);
        byte[] cell5 = engine.trySlice("textures/particle/explosion_5.png", p -> EXPLOSION);
        byte[] cell15 = engine.trySlice("textures/particle/explosion_15.png", p -> EXPLOSION);

        assertNotNull(cell0);
        assertNotNull(cell5);
        assertNotNull(cell15);
        for (byte[] cell : new byte[][] {cell0, cell5, cell15}) {
            assertEquals(32, PngTestUtil.width(cell));
            assertEquals(32, PngTestUtil.height(cell));
        }
        assertEquals(PngTestUtil.rgb(EXPLOSION, 0, 0), PngTestUtil.rgb(cell0, 0, 0));
        assertEquals(PngTestUtil.rgb(EXPLOSION, 32, 32), PngTestUtil.rgb(cell5, 0, 0));
        assertEquals(PngTestUtil.rgb(EXPLOSION, 96, 96), PngTestUtil.rgb(cell15, 0, 0));
        // Only 0..15 exist.
        assertNull(engine.trySlice("textures/particle/explosion_16.png", p -> EXPLOSION));
    }

    @Test
    void explosionTargetsAreListed() {
        List<String> targets = engine.sliceTargetsUnder("textures/particle", p -> true);
        assertEquals(16, targets.size());
        assertTrue(targets.contains("textures/particle/explosion_0.png"));
        assertTrue(targets.contains("textures/particle/explosion_15.png"));
    }
}
