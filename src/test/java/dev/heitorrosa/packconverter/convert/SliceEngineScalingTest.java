package dev.heitorrosa.packconverter.convert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests for source-relative box scaling in {@link SliceEngine}: spec boxes are
 * calibrated against the declared vanilla source size (256x256 for widgets),
 * and must be scaled by the ACTUAL source size / DECLARED size so 2x HD packs
 * slice at the scaled cells and downscaled packs scale down too. When the
 * actual size equals the declared size the boxes are unchanged.
 */
class SliceEngineScalingTest {

    private static final int DECLARED = 256;

    /** widgets-like source: hotbar (0,0,182x22), selection (0,22,24x23), offhand_left (24,22,29x24). */
    private static SliceEngine engineWith(byte[] source, int declaredWidth, int declaredHeight) {
        SliceSpec spec = new SliceSpec("textures/gui/widgets.png", declaredWidth, declaredHeight, List.of(
                new SliceTarget("textures/gui/sprites/hud/hotbar.png", 0, 0, 182, 22),
                new SliceTarget("textures/gui/sprites/hud/hotbar_selection.png", 0, 22, 24, 23),
                new SliceTarget("textures/gui/sprites/hud/hotbar_offhand_left.png", 24, 22, 29, 24)));
        return new SliceEngine(new SliceSpecLoader(List.of(spec)), new Slicer());
    }

    @Test
    void upScalesBoxesForTwoTimesSource() {
        byte[] source = PngTestUtil.png(512, 512); // 2x the declared 256x256
        SliceEngine engine = engineWith(source, DECLARED, DECLARED);

        byte[] hotbar = engine.trySlice("textures/gui/sprites/hud/hotbar.png", p -> source);
        assertNotNull(hotbar);
        assertEquals(364, PngTestUtil.width(hotbar));
        assertEquals(44, PngTestUtil.height(hotbar));
        assertEquals(PngTestUtil.rgb(source, 100, 30), PngTestUtil.rgb(hotbar, 100, 30));

        // (0,22,24x23) -> (0,44,48x46): proves the y offset scales too.
        byte[] selection = engine.trySlice("textures/gui/sprites/hud/hotbar_selection.png", p -> source);
        assertNotNull(selection);
        assertEquals(48, PngTestUtil.width(selection));
        assertEquals(46, PngTestUtil.height(selection));
        assertEquals(PngTestUtil.rgb(source, 0, 44), PngTestUtil.rgb(selection, 0, 0));

        // (24,22,29x24) -> (48,44,58x48): proves the x offset scales too.
        byte[] offhand = engine.trySlice("textures/gui/sprites/hud/hotbar_offhand_left.png", p -> source);
        assertNotNull(offhand);
        assertEquals(58, PngTestUtil.width(offhand));
        assertEquals(48, PngTestUtil.height(offhand));
        assertEquals(PngTestUtil.rgb(source, 48, 44), PngTestUtil.rgb(offhand, 0, 0));
    }

    @Test
    void downScalesBoxesForHalfSource() {
        byte[] source = PngTestUtil.png(128, 128); // 0.5x the declared 256x256
        SliceEngine engine = engineWith(source, DECLARED, DECLARED);

        byte[] hotbar = engine.trySlice("textures/gui/sprites/hud/hotbar.png", p -> source);
        assertNotNull(hotbar);
        assertEquals(91, PngTestUtil.width(hotbar));
        assertEquals(11, PngTestUtil.height(hotbar));
        assertEquals(PngTestUtil.rgb(source, 0, 0), PngTestUtil.rgb(hotbar, 0, 0));

        // (0,22,24x23) -> (0,11,12x12): y offset 22*0.5=11, height 23*0.5 rounds to 12.
        byte[] selection = engine.trySlice("textures/gui/sprites/hud/hotbar_selection.png", p -> source);
        assertNotNull(selection);
        assertEquals(12, PngTestUtil.width(selection));
        assertEquals(12, PngTestUtil.height(selection));
        assertEquals(PngTestUtil.rgb(source, 0, 11), PngTestUtil.rgb(selection, 0, 0));
    }

    @Test
    void boxesUnchangedWhenActualEqualsDeclared() {
        byte[] source = PngTestUtil.png(256, 256);
        SliceEngine engine = engineWith(source, DECLARED, DECLARED);

        byte[] hotbar = engine.trySlice("textures/gui/sprites/hud/hotbar.png", p -> source);
        assertNotNull(hotbar);
        assertEquals(182, PngTestUtil.width(hotbar));
        assertEquals(22, PngTestUtil.height(hotbar));
        byte[] selection = engine.trySlice("textures/gui/sprites/hud/hotbar_selection.png", p -> source);
        assertNotNull(selection);
        assertEquals(PngTestUtil.rgb(source, 0, 22), PngTestUtil.rgb(selection, 0, 0));
    }

    @Test
    void zeroDeclaredSizeSkipsScaling() {
        // Guard: a spec without declared dims must behave as unscaled (1.0).
        byte[] source = PngTestUtil.png(256, 256);
        SliceEngine engine = engineWith(source, 0, 0);

        byte[] hotbar = engine.trySlice("textures/gui/sprites/hud/hotbar.png", p -> source);
        assertNotNull(hotbar);
        assertEquals(182, PngTestUtil.width(hotbar));
        assertEquals(22, PngTestUtil.height(hotbar));
    }

    @Test
    void realLoaderScalesWidgetsAtlas() {
        // End to end: gui.json declares widgets.png as 256x256; a 2x pack's
        // 512x512 atlas must yield a 364x44 hotbar.
        byte[] source = PngTestUtil.png(512, 512);
        SliceEngine engine = new SliceEngine(SliceSpecLoader.fromDefaultResources(), new Slicer());

        byte[] hotbar = engine.trySlice("textures/gui/sprites/hud/hotbar.png", p -> source);
        assertNotNull(hotbar);
        assertEquals(364, PngTestUtil.width(hotbar));
        assertEquals(44, PngTestUtil.height(hotbar));
        assertEquals(PngTestUtil.rgb(source, 100, 30), PngTestUtil.rgb(hotbar, 100, 30));
    }
}
