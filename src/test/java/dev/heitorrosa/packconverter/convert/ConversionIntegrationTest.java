package dev.heitorrosa.packconverter.convert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Integration test: wires {@link RenameMap#fromDefaultResources()} +
 * {@link PathRemapper} over the REAL mapping data and asserts the golden
 * renames end-to-end.
 */
class ConversionIntegrationTest {

    private final RenameMap map = RenameMap.fromDefaultResources();
    private final PathRemapper remapper = new PathRemapper(map);

    @Test
    void realDataGoldenRenames() {
        assertEquals("oak_planks", map.rename("planks_oak"));
        assertEquals("stone_bricks", map.rename("stonebrick"));
        assertEquals("grass_block_top", map.rename("grass_top"));
        assertEquals("golden_apple", map.rename("apple_golden"));
        assertEquals("totally_unknown_name", map.rename("totally_unknown_name"));
    }

    @Test
    void realDataMultiStepChain() {
        // tall_grass -> grass (1_13) -> short_grass (1_20_3) in blocks.json
        assertEquals("short_grass", map.rename("tall_grass"));
        // dye_black -> ink_sac (1_13) -> black_dye (1_14) in items.json
        assertEquals("black_dye", map.rename("dye_black"));
    }

    @Test
    void realDataGoldenPathRemapping() {
        List<String> legacy = Arrays.asList(
                "textures/blocks/planks_oak.png",
                "textures/items/apple_golden.png",
                "minecraft:textures/blocks/grass_top.png"
        );
        List<String> modern = Arrays.asList(
                "textures/block/oak_planks.png",
                "textures/item/golden_apple.png",
                "minecraft:textures/block/grass_block_top.png"
        );
        for (int i = 0; i < legacy.size(); i++) {
            assertEquals(modern.get(i), remapper.remapPath(legacy.get(i)));
        }
    }

    @Test
    void detectorFlagsRealLegacyPack() {
        OldPackDetector detector = new OldPackDetector();
        assertTrue(detector.needsConversion("{\"pack\":{\"pack_format\":2,\"description\":\"1.8 pack\"}}",
                Arrays.asList("assets/minecraft/textures/blocks/planks_oak.png")));
    }
}
