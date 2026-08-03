package dev.heitorrosa.packconverter.convert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link RenameMap}: loads /mappings/blocks.json + /mappings/items.json
 * from the classpath and chains every version section in ascending numeric order.
 */
class RenameMapTest {

    private final RenameMap map = RenameMap.fromDefaultResources();

    @Test
    void loadsFromDefaultClasspathResources() {
        assertNotNull(map);
    }

    @Test
    void renamesOneEightBlockNames() {
        assertEquals("oak_planks", map.rename("planks_oak"));
        assertEquals("stone_bricks", map.rename("stonebrick"));
        assertEquals("grass_block_top", map.rename("grass_top"));
    }

    @Test
    void renamesOneEightItemNames() {
        assertEquals("golden_apple", map.rename("apple_golden"));
        assertEquals("cooked_beef", map.rename("beef_cooked"));
    }

    @Test
    void unknownNameIsReturnedUnchanged() {
        assertEquals("totally_unknown_name", map.rename("totally_unknown_name"));
    }

    @Test
    void chainsAcrossVersionSteps_blocksJson() {
        // Real chain in blocks.json: tall_grass -> grass (1_13) -> short_grass (1_20_3)
        assertEquals("short_grass", map.rename("tall_grass"));
    }

    @Test
    void chainsAcrossVersionSteps_itemsJson() {
        // Real chain in items.json: dye_black -> ink_sac (1_13) -> black_dye (1_14)
        assertEquals("black_dye", map.rename("dye_black"));
    }
}
