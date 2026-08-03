package dev.heitorrosa.packconverter.convert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link PathRemapper}: folder normalization, short forms,
 * namespace/extension preservation, and legacy-path detection.
 */
class PathRemapperTest {

    private static PathRemapper remapper;

    @BeforeAll
    static void setUp() {
        remapper = new PathRemapper(RenameMap.fromDefaultResources());
    }

    @Test
    void remapsBlocksFolderTexture() {
        assertEquals("textures/block/oak_planks.png", remapper.remapPath("textures/blocks/planks_oak.png"));
    }

    @Test
    void remapsItemsFolderTexture() {
        assertEquals("textures/item/golden_apple.png", remapper.remapPath("textures/items/apple_golden.png"));
    }

    @Test
    void armorFoldersPassThroughUnchanged() {
        // 26.2 has no models/armor; armor layers are handled by the
        // ArmorExpansion alias table, so both 1.8 spellings stay put here.
        assertEquals("textures/armor/iron_layer_1.png", remapper.remapPath("textures/armor/iron_layer_1.png"));
        assertEquals("textures/models/armor/iron_layer_1.png", remapper.remapPath("textures/models/armor/iron_layer_1.png"));
    }

    @Test
    void remapsMojangLogo() {
        assertEquals("textures/gui/title/mojangstudios.png", remapper.remapPath("textures/gui/title/mojang.png"));
    }

    @Test
    void remapsSunToCelestialFolder() {
        assertEquals("textures/environment/celestial/sun.png", remapper.remapPath("textures/environment/sun.png"));
    }

    @Test
    void remapsStonebrickTexture() {
        assertEquals("textures/block/stone_bricks.png", remapper.remapPath("textures/blocks/stonebrick.png"));
    }

    @Test
    void alreadyModernPathIsUnchanged() {
        assertEquals("textures/block/oak_planks.png", remapper.remapPath("textures/block/oak_planks.png"));
    }

    @Test
    void nonRenameFolderIsUnchanged() {
        assertEquals("textures/entity/creeper/creeper.png", remapper.remapPath("textures/entity/creeper/creeper.png"));
    }

    @Test
    void remapsShortBlockForm() {
        assertEquals("block/oak_planks", remapper.remapPath("block/planks_oak"));
    }

    @Test
    void remapsLegacyShortBlockPluralForm() {
        assertEquals("block/oak_planks", remapper.remapPath("blocks/planks_oak"));
    }

    @Test
    void remapsShortItemForm() {
        assertEquals("item/golden_apple", remapper.remapPath("item/apple_golden"));
    }

    @Test
    void remapsLegacyShortItemPluralForm() {
        assertEquals("item/golden_apple", remapper.remapPath("items/apple_golden"));
    }

    @Test
    void preservesNamespacePrefix() {
        assertEquals("minecraft:textures/block/grass_block_top.png", remapper.remapPath("minecraft:textures/blocks/grass_top.png"));
    }

    @Test
    void remapsMcmetaCompanionFile() {
        assertEquals("textures/block/oak_planks.png.mcmeta", remapper.remapPath("textures/blocks/planks_oak.png.mcmeta"));
    }

    @Test
    void modernCelestialSunIsNotRemappedAgain() {
        assertEquals("textures/environment/celestial/sun.png",
                remapper.remapPath("textures/environment/celestial/sun.png"));
    }

    @Test
    void modernMojangstudiosIsNotRemappedAgain() {
        assertEquals("textures/gui/title/mojangstudios.png",
                remapper.remapPath("textures/gui/title/mojangstudios.png"));
    }

    @Test
    void legacyPathDetectionIsTrueForLegacyPaths() {
        assertTrue(remapper.isLegacyPath("textures/blocks/x.png"));
        assertTrue(remapper.isLegacyPath("textures/items/y.png"));
        assertTrue(remapper.isLegacyPath("blocks/z"));
        assertTrue(remapper.isLegacyPath("items/w"));
    }

    @Test
    void legacyPathDetectionIsFalseForModernPaths() {
        assertFalse(remapper.isLegacyPath("textures/block/oak_planks.png"));
        assertFalse(remapper.isLegacyPath("textures/entity/creeper/creeper.png"));
        assertFalse(remapper.isLegacyPath("block/planks_oak"));
    }
}
