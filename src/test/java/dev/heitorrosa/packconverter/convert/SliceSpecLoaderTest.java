package dev.heitorrosa.packconverter.convert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link SliceSpecLoader} against the REAL mapping resources:
 * gui.json is a flat array (40 specs), the other four are single-spec objects.
 */
class SliceSpecLoaderTest {

    private final SliceSpecLoader loader = SliceSpecLoader.fromDefaultResources();

    @Test
    void guiJsonParsesAllFortySpecs() {
        // 40 gui.json specs + particles + mob_effect + map_icons + moon_phases;
        // mob_effect's source (gui/container/inventory.png) merges into the
        // existing gui.json source, so it adds no new key.
        assertEquals(43, loader.index().size());
    }

    @Test
    void widgetsIndexHasHotbarTarget() {
        List<SliceTarget> targets = loader.index().get("textures/gui/widgets.png");
        assertNotNull(targets);
        SliceTarget hotbar = find(targets, "textures/gui/sprites/hud/hotbar.png");
        assertEquals(0, hotbar.x());
        assertEquals(0, hotbar.y());
        assertEquals(182, hotbar.width());
        assertEquals(22, hotbar.height());
    }

    @Test
    void particlesIndexHasNinetyOneTargets() {
        List<SliceTarget> targets = loader.index().get("textures/particle/particles.png");
        assertNotNull(targets);
        assertEquals(91, targets.size());
        assertNotNull(find(targets, "textures/particle/generic_0.png"));
        assertNotNull(find(targets, "textures/particle/effect_7.png"));
    }

    @Test
    void inventorySourceMergesGuiAndMobEffectTargets() {
        // gui.json container/inventory.png (6 targets) + mob_effect.json (27).
        List<SliceTarget> targets = loader.index().get("textures/gui/container/inventory.png");
        assertNotNull(targets);
        assertEquals(33, targets.size());
    }

    @Test
    void mapIconsAndMoonPhasesIndexed() {
        assertNotNull(find(loader.index().get("textures/map/map_icons.png"),
                "textures/map/decorations/player.png"));
        SliceTarget fullMoon = find(loader.index().get("textures/environment/moon_phases.png"),
                "textures/environment/celestial/moon/full_moon.png");
        assertEquals(0, fullMoon.x());
        assertEquals(0, fullMoon.y());
        assertEquals(32, fullMoon.width());
        assertEquals(32, fullMoon.height());
    }

    @Test
    void reverseIndexMapsTargetToSourceAndBox() {
        SliceEntry hotbar = loader.reverseIndex().get("textures/gui/sprites/hud/hotbar.png");
        assertNotNull(hotbar);
        assertEquals("textures/gui/widgets.png", hotbar.sourcePath());
        assertEquals(0, hotbar.target().x());
        assertEquals(0, hotbar.target().y());
    }

    @Test
    void reverseIndexResolvesMobEffectSpeed() {
        SliceEntry speed = loader.reverseIndex().get("textures/mob_effect/speed.png");
        assertNotNull(speed);
        assertEquals("textures/gui/container/inventory.png", speed.sourcePath());
        assertEquals(0, speed.target().x());
        assertEquals(198, speed.target().y());
        assertEquals(18, speed.target().width());
        assertEquals(18, speed.target().height());
    }

    @Test
    void reverseIndexResolvesMoonPhase() {
        SliceEntry waning = loader.reverseIndex().get("textures/environment/celestial/moon/waning_gibbous.png");
        assertNotNull(waning);
        assertEquals("textures/environment/moon_phases.png", waning.sourcePath());
        assertEquals(32, waning.target().x());
    }

    @Test
    void unknownTargetNotInReverseIndex() {
        assertNull(loader.reverseIndex().get("textures/gui/sprites/hud/does_not_exist.png"));
    }

    @Test
    void indicesAreImmutable() {
        assertTrue(() -> {
            try {
                loader.index().put("x", List.of());
                return false;
            } catch (UnsupportedOperationException e) {
                return true;
            }
        }, "index must be unmodifiable");
    }

    @Test
    void explicitSpecsConstructLoader() {
        SliceSpec spec = new SliceSpec("textures/gui/widgets.png",
                List.of(new SliceTarget("textures/gui/sprites/hud/hotbar.png", 0, 0, 182, 22)));
        SliceSpecLoader explicit = new SliceSpecLoader(List.of(spec));

        Map<String, List<SliceTarget>> index = explicit.index();
        assertEquals(1, index.size());
        assertEquals(1, index.get("textures/gui/widgets.png").size());
        assertNotNull(explicit.reverseIndex().get("textures/gui/sprites/hud/hotbar.png"));
    }

    private static SliceTarget find(List<SliceTarget> targets, String path) {
        return targets.stream().filter(t -> t.targetPath().equals(path)).findFirst().orElse(null);
    }
}
