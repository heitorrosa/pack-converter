package dev.heitorrosa.packconverter.convert;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ArmorExpansion}: one-to-many armor layer aliasing (both
 * 1.8 source spellings), the reverse lookup, horse armor, and the menu
 * background / mojang logo / sun copy targets that reuse the same mechanism.
 */
class ArmorExpansionTest {

    private final ArmorExpansion expansion = new ArmorExpansion();

    @Test
    void ironLayer1ExpandsToBothHumanoidTargets() {
        String[] targets = expansion.targetsFor("textures/models/armor/iron_layer_1.png");
        assertArrayEquals(new String[] {
                "textures/entity/equipment/humanoid/iron.png",
                "textures/entity/equipment/humanoid_baby/iron.png"}, targets);
    }

    @Test
    void legacyArmorFolderSpellingExpandsIdentically() {
        assertArrayEquals(
                expansion.targetsFor("textures/models/armor/iron_layer_1.png"),
                expansion.targetsFor("textures/armor/iron_layer_1.png"));
    }

    @Test
    void ironLayer2ExpandsToLeggingsOnly() {
        assertArrayEquals(new String[] {"textures/entity/equipment/humanoid_leggings/iron.png"},
                expansion.targetsFor("textures/models/armor/iron_layer_2.png"));
    }

    @Test
    void leatherOverlayExpands() {
        String[] targets = expansion.targetsFor("textures/models/armor/leather_layer_1_overlay.png");
        assertArrayEquals(new String[] {
                "textures/entity/equipment/humanoid/leather_overlay.png",
                "textures/entity/equipment/humanoid_baby/leather_overlay.png"}, targets);
        assertArrayEquals(new String[] {"textures/entity/equipment/humanoid_leggings/leather_overlay.png"},
                expansion.targetsFor("textures/models/armor/leather_layer_2_overlay.png"));
    }

    @Test
    void unknownSourceHasNoTargets() {
        assertArrayEquals(new String[0], expansion.targetsFor("textures/entity/creeper/creeper.png"));
        assertArrayEquals(new String[0], expansion.targetsFor("textures/entity/equipment/humanoid/iron.png"));
    }

    @Test
    void sourceForTargetReverseLookup() {
        assertEquals("textures/models/armor/iron_layer_1.png",
                expansion.sourceForTarget("textures/entity/equipment/humanoid/iron.png"));
        assertEquals("textures/models/armor/iron_layer_1.png",
                expansion.sourceForTarget("textures/entity/equipment/humanoid_baby/iron.png"));
        assertNull(expansion.sourceForTarget("textures/entity/equipment/humanoid/nope.png"));
    }

    @Test
    void horseArmorExpands() {
        String[] iron = expansion.targetsFor("textures/models/armor/horse/armor/horse_armor_iron.png");
        assertArrayEquals(new String[] {"textures/entity/equipment/horse_body/iron.png"}, iron);
        assertEquals("textures/models/armor/horse/armor/horse_armor_gold.png",
                expansion.sourceForTarget("textures/entity/equipment/horse_body/gold.png"));
        assertEquals("textures/models/armor/horse/armor/horse_armor_diamond.png",
                expansion.sourceForTarget("textures/entity/equipment/horse_body/diamond.png"));
    }

    @Test
    void menuBackgroundCopiesUseExpansion() {
        assertArrayEquals(new String[] {
                "textures/gui/menu_background.png",
                "textures/gui/inworld_menu_background.png"},
                expansion.targetsFor("textures/gui/options_background.png"));
        assertEquals("textures/gui/options_background.png",
                expansion.sourceForTarget("textures/gui/menu_background.png"));
        assertEquals("textures/gui/options_background.png",
                expansion.sourceForTarget("textures/gui/inworld_menu_background.png"));
    }

    @Test
    void mojangLogoAndSunCopiesUseExpansion() {
        assertArrayEquals(new String[] {"textures/gui/title/mojangstudios.png"},
                expansion.targetsFor("textures/gui/title/mojang.png"));
        assertEquals("textures/gui/title/mojang.png",
                expansion.sourceForTarget("textures/gui/title/mojangstudios.png"));
        assertArrayEquals(new String[] {"textures/environment/celestial/sun.png"},
                expansion.targetsFor("textures/environment/sun.png"));
        assertEquals("textures/environment/sun.png",
                expansion.sourceForTarget("textures/environment/celestial/sun.png"));
    }

    @Test
    void targetsUnderListsHumanoidDirectory() {
        List<String> humanoid = expansion.targetsUnder("textures/entity/equipment/humanoid");
        assertTrue(humanoid.contains("textures/entity/equipment/humanoid/iron.png"));
        assertTrue(humanoid.contains("textures/entity/equipment/humanoid/chainmail.png"));
        assertTrue(humanoid.contains("textures/entity/equipment/humanoid/leather_overlay.png"));
        // humanoid_baby and leggings live in sibling directories.
        assertTrue(humanoid.stream().noneMatch(t -> t.contains("humanoid_baby")));
        assertTrue(humanoid.stream().noneMatch(t -> t.contains("leggings")));
    }

    @Test
    void targetsUnderIncludesCopyTargets() {
        assertTrue(expansion.targetsUnder("textures/gui/title").contains("textures/gui/title/mojangstudios.png"));
        assertTrue(expansion.targetsUnder("textures/gui").contains("textures/gui/menu_background.png"));
        assertTrue(expansion.targetsUnder("textures/environment").contains("textures/environment/celestial/sun.png"));
        assertTrue(expansion.targetsUnder("textures/entity/equipment").contains("textures/entity/equipment/horse_body/gold.png"));
    }
}
