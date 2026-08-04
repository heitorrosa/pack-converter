package dev.heitorrosa.packconverter.convert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests for the compass/clock conversion: 1.8.9 ships the compass as a
 * 16x512 atlas of 32 frames (16x16 each) and the clock as a 16x1024 atlas of
 * 64 frames; 26.2 requests one texture per frame
 * ({@code textures/item/compass_00.png} ... {@code compass_31},
 * {@code clock_00} ... {@code clock_63}). Frames are index-aligned, so frame
 * i is sliced from row band i of the legacy atlas.
 */
class SliceCompassClockTest {

    private static final SliceSpecLoader LOADER = SliceSpecLoader.fromDefaultResources();
    private static final SliceEngine ENGINE = new SliceEngine(LOADER, new Slicer());

    @Test
    void compassJsonIndexesThirtyTwoFrames() {
        List<SliceTarget> targets = LOADER.index().get("textures/items/compass.png");
        assertNotNull(targets);
        assertEquals(32, targets.size());
        SliceTarget first = find(targets, "textures/item/compass_00.png");
        assertEquals(0, first.x());
        assertEquals(0, first.y());
        assertEquals(16, first.width());
        assertEquals(16, first.height());
        SliceTarget last = find(targets, "textures/item/compass_31.png");
        assertEquals(0, last.x());
        assertEquals(31 * 16, last.y());
        assertEquals(16, last.height());
    }

    @Test
    void clockJsonIndexesSixtyFourFrames() {
        List<SliceTarget> targets = LOADER.index().get("textures/items/clock.png");
        assertNotNull(targets);
        assertEquals(64, targets.size());
        SliceTarget first = find(targets, "textures/item/clock_00.png");
        assertEquals(0, first.y());
        SliceTarget last = find(targets, "textures/item/clock_63.png");
        assertEquals(63 * 16, last.y());
        assertEquals(16, last.height());
    }

    @Test
    void reverseIndexResolvesCompassFrame() {
        SliceEntry entry = LOADER.reverseIndex().get("textures/item/compass_07.png");
        assertNotNull(entry);
        assertEquals("textures/items/compass.png", entry.sourcePath());
        assertEquals(7 * 16, entry.target().y());
        assertEquals(16, entry.target().width());
        assertEquals(16, entry.target().height());
    }

    @Test
    void reverseIndexResolvesClockFrame() {
        SliceEntry entry = LOADER.reverseIndex().get("textures/item/clock_63.png");
        assertNotNull(entry);
        assertEquals("textures/items/clock.png", entry.sourcePath());
        assertEquals(63 * 16, entry.target().y());
        assertEquals(16, entry.target().width());
    }

    @Test
    void compassFrameSlicesCorrectBand() {
        byte[] atlas = PngTestUtil.png(16, 512); // 32 bands of 16 rows

        byte[] frame00 = ENGINE.trySlice("textures/item/compass_00.png", p -> atlas);
        assertNotNull(frame00);
        assertEquals(16, PngTestUtil.width(frame00));
        assertEquals(16, PngTestUtil.height(frame00));
        assertEquals(PngTestUtil.rgb(atlas, 0, 0), PngTestUtil.rgb(frame00, 0, 0));

        byte[] frame07 = ENGINE.trySlice("textures/item/compass_07.png", p -> atlas);
        assertNotNull(frame07);
        assertEquals(PngTestUtil.rgb(atlas, 0, 7 * 16), PngTestUtil.rgb(frame07, 0, 0));

        byte[] frame31 = ENGINE.trySlice("textures/item/compass_31.png", p -> atlas);
        assertNotNull(frame31);
        assertEquals(PngTestUtil.rgb(atlas, 0, 31 * 16), PngTestUtil.rgb(frame31, 0, 0));
    }

    @Test
    void clockFrameSlicesCorrectBand() {
        byte[] atlas = PngTestUtil.png(16, 1024); // 64 bands of 16 rows

        byte[] frame00 = ENGINE.trySlice("textures/item/clock_00.png", p -> atlas);
        assertNotNull(frame00);
        assertEquals(PngTestUtil.rgb(atlas, 0, 0), PngTestUtil.rgb(frame00, 0, 0));

        byte[] frame63 = ENGINE.trySlice("textures/item/clock_63.png", p -> atlas);
        assertNotNull(frame63);
        assertEquals(16, PngTestUtil.width(frame63));
        assertEquals(16, PngTestUtil.height(frame63));
        assertEquals(PngTestUtil.rgb(atlas, 0, 63 * 16), PngTestUtil.rgb(frame63, 0, 0));
    }

    @Test
    void hdCompassScalesFramesToTwoTimes() {
        // 32x1024 = 2x the declared 16x512; boxes must scale (y = frame*16*2).
        byte[] atlas = PngTestUtil.png(32, 1024);

        byte[] frame00 = ENGINE.trySlice("textures/item/compass_00.png", p -> atlas);
        assertNotNull(frame00);
        assertEquals(32, PngTestUtil.width(frame00));
        assertEquals(32, PngTestUtil.height(frame00));
        assertEquals(PngTestUtil.rgb(atlas, 0, 0), PngTestUtil.rgb(frame00, 0, 0));

        byte[] frame07 = ENGINE.trySlice("textures/item/compass_07.png", p -> atlas);
        assertNotNull(frame07);
        assertEquals(32, PngTestUtil.width(frame07));
        assertEquals(32, PngTestUtil.height(frame07));
        // y offset scaled: 7*16*2 = 224.
        assertEquals(PngTestUtil.rgb(atlas, 0, 224), PngTestUtil.rgb(frame07, 0, 0));

        byte[] frame31 = ENGINE.trySlice("textures/item/compass_31.png", p -> atlas);
        assertNotNull(frame31);
        assertEquals(32, PngTestUtil.width(frame31));
        assertEquals(PngTestUtil.rgb(atlas, 0, 992), PngTestUtil.rgb(frame31, 0, 0));
    }

    @Test
    void sliceTargetsUnderItemDirectoryListsAllFrames() {
        List<String> targets = ENGINE.sliceTargetsUnder("textures/item", p -> true);
        assertEquals(96, targets.size());
        assertTrue(targets.contains("textures/item/compass_00.png"));
        assertTrue(targets.contains("textures/item/compass_31.png"));
        assertTrue(targets.contains("textures/item/clock_00.png"));
        assertTrue(targets.contains("textures/item/clock_63.png"));
    }

    @Test
    void compassAndClockFramesNotInRenameMap() {
        // The slice engine must intercept compass_*/clock_* before the item
        // rename table would touch them; the names must not be renames.
        RenameMap map = RenameMap.fromDefaultResources();
        assertEquals("compass_00", map.rename("compass_00"));
        assertEquals("clock_63", map.rename("clock_63"));
        assertTrue(map.reverseIndex().getOrDefault("compass_00", List.of()).isEmpty());
        assertTrue(map.reverseIndex().getOrDefault("clock_63", List.of()).isEmpty());
    }

    private static SliceTarget find(List<SliceTarget> targets, String path) {
        return targets.stream().filter(t -> t.targetPath().equals(path)).findFirst().orElse(null);
    }
}
