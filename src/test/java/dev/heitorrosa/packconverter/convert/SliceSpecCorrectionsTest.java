package dev.heitorrosa.packconverter.convert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * Behavioral tests for the corrected mapping specs against the REAL resources:
 * map_icons.json (8 real 1.8.9 icons on a 32x32 atlas), mob_effect.json
 * (health_boost at the (36,234) cell, non-1.8.9 effects dropped) and gui.json
 * (the two 26.2-renamed effect_background sprites dropped).
 */
class SliceSpecCorrectionsTest {

    private static final byte[] MAP_ICONS = PngTestUtil.png(32, 32);
    private static final byte[] INVENTORY = PngTestUtil.png(256, 256);

    private final SliceEngine engine = new SliceEngine(
            SliceSpecLoader.fromDefaultResources(), new Slicer());

    @Test
    void mapIconsServesRealEightIcons() {
        byte[] player = engine.trySlice("textures/map/decorations/player.png", p -> MAP_ICONS);
        assertNotNull(player);
        assertEquals(8, PngTestUtil.width(player));
        assertEquals(8, PngTestUtil.height(player));
        // (0,0) cell: corner and far-corner pixels match the source cell.
        assertEquals(PngTestUtil.rgb(MAP_ICONS, 0, 0), PngTestUtil.rgb(player, 0, 0));
        assertEquals(PngTestUtil.rgb(MAP_ICONS, 7, 7), PngTestUtil.rgb(player, 7, 7));

        byte[] offLimits = engine.trySlice("textures/map/decorations/player_off_limits.png",
                p -> MAP_ICONS);
        assertNotNull(offLimits);
        assertEquals(PngTestUtil.rgb(MAP_ICONS, 24, 8), PngTestUtil.rgb(offLimits, 0, 0));

        byte[] frame = engine.trySlice("textures/map/decorations/frame.png", p -> MAP_ICONS);
        assertNotNull(frame);
        assertEquals(PngTestUtil.rgb(MAP_ICONS, 8, 0), PngTestUtil.rgb(frame, 0, 0));
    }

    @Test
    void mapIconsDropsPlaceholderDecorations() {
        // Solid-blue 1.8.9 placeholder cells: the game must fall through to
        // the modern art instead of slicing the placeholder.
        assertNull(engine.trySlice("textures/map/decorations/white_banner.png", p -> MAP_ICONS));
        assertNull(engine.trySlice("textures/map/decorations/woodland_mansion.png", p -> MAP_ICONS));
    }

    @Test
    void mapIconsScalesWithDeclaredThirtyTwo() {
        // The corrected spec declares a 32x32 atlas: a 2x pack's 64x64
        // map_icons.png must yield 16x16 icon cells, and player_off_limits
        // (24,8) must scale to (48,16).
        byte[] source = PngTestUtil.png(64, 64);
        byte[] player = engine.trySlice("textures/map/decorations/player.png", p -> source);
        assertNotNull(player);
        assertEquals(16, PngTestUtil.width(player));
        assertEquals(16, PngTestUtil.height(player));
        byte[] offLimits = engine.trySlice("textures/map/decorations/player_off_limits.png", p -> source);
        assertNotNull(offLimits);
        assertEquals(PngTestUtil.rgb(source, 48, 16), PngTestUtil.rgb(offLimits, 0, 0));
    }

    @Test
    void mobEffectHealthBoostSlicesCorrectCell() {
        // Corrected from the wrong (126,234) decoration cell to (36,234).
        byte[] boost = engine.trySlice("textures/mob_effect/health_boost.png", p -> INVENTORY);
        assertNotNull(boost);
        assertEquals(18, PngTestUtil.width(boost));
        assertEquals(18, PngTestUtil.height(boost));
        assertEquals(PngTestUtil.rgb(INVENTORY, 36, 234), PngTestUtil.rgb(boost, 0, 0));
    }

    @Test
    void mobEffectDropsNonExistentEffects() {
        // absorption/glowing/levitation/luck/unluck do not exist in 1.8.9 and
        // slice non-icon decoration; they must fall through to vanilla.
        for (String effect : new String[] {"absorption", "glowing", "levitation", "luck", "unluck"}) {
            assertNull(engine.trySlice("textures/mob_effect/" + effect + ".png", p -> INVENTORY));
        }
    }

    @Test
    void guiDropsRenamedEffectBackgroundSprites() {
        // 26.2 renamed these sprites; no counterpart exists to slice.
        assertNull(engine.trySlice(
                "textures/gui/sprites/container/inventory/effect_background_large.png", p -> INVENTORY));
        assertNull(engine.trySlice(
                "textures/gui/sprites/container/inventory/effect_background_small.png", p -> INVENTORY));
        // The surviving hud effect backgrounds are still served.
        assertNotNull(engine.trySlice("textures/gui/sprites/hud/effect_background.png", p -> INVENTORY));
        assertNotNull(engine.trySlice("textures/gui/sprites/hud/effect_background_ambient.png", p -> INVENTORY));
    }
}
