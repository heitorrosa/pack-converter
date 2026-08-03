package dev.heitorrosa.packconverter.convert;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One-to-many path aliasing with no slicing: a legacy source file is served
 * verbatim under every modern target path. Covers the 26.2 armor layer
 * textures ({@code textures/entity/equipment/<layer>/<material>.png}, served
 * directly, not from an atlas), the horse armor layers, and the menu
 * background / mojang logo / sun copies that reuse the same mechanism.
 *
 * <p>Armor sources accept both 1.8 spellings ({@code textures/models/armor/}
 * and {@code textures/armor/}); the reverse lookup always reports the
 * {@code textures/models/armor/} form. Existence of the source is the caller's
 * responsibility. Immutable after construction (thread-safe).
 */
public final class ArmorExpansion {

    private static final String ARMOR_DIR = "textures/models/armor/";

    private final Map<String, List<String>> forward;
    private final Map<String, String> reverse;

    public ArmorExpansion() {
        Map<String, List<String>> forward = new HashMap<>();
        Map<String, String> reverse = new HashMap<>();
        // Player armor layers.
        putArmor(forward, reverse, "iron_layer_1.png", "humanoid/iron.png", "humanoid_baby/iron.png");
        putArmor(forward, reverse, "iron_layer_2.png", "humanoid_leggings/iron.png");
        putArmor(forward, reverse, "chainmail_layer_1.png", "humanoid/chainmail.png", "humanoid_baby/chainmail.png");
        putArmor(forward, reverse, "chainmail_layer_2.png", "humanoid_leggings/chainmail.png");
        putArmor(forward, reverse, "diamond_layer_1.png", "humanoid/diamond.png", "humanoid_baby/diamond.png");
        putArmor(forward, reverse, "diamond_layer_2.png", "humanoid_leggings/diamond.png");
        putArmor(forward, reverse, "gold_layer_1.png", "humanoid/gold.png", "humanoid_baby/gold.png");
        putArmor(forward, reverse, "gold_layer_2.png", "humanoid_leggings/gold.png");
        putArmor(forward, reverse, "leather_layer_1.png", "humanoid/leather.png", "humanoid_baby/leather.png");
        putArmor(forward, reverse, "leather_layer_2.png", "humanoid_leggings/leather.png");
        putArmor(forward, reverse, "leather_layer_1_overlay.png",
                "humanoid/leather_overlay.png", "humanoid_baby/leather_overlay.png");
        putArmor(forward, reverse, "leather_layer_2_overlay.png", "humanoid_leggings/leather_overlay.png");
        // Horse armor (same armor-relative spelling rules).
        putArmor(forward, reverse, "horse/armor/horse_armor_iron.png", "horse_body/iron.png");
        putArmor(forward, reverse, "horse/armor/horse_armor_gold.png", "horse_body/gold.png");
        putArmor(forward, reverse, "horse/armor/horse_armor_diamond.png", "horse_body/diamond.png");
        // Title/menu copy targets (full pack-relative sources).
        putFull(forward, reverse, "textures/gui/options_background.png",
                "textures/gui/menu_background.png", "textures/gui/inworld_menu_background.png");
        putFull(forward, reverse, "textures/gui/title/mojang.png", "textures/gui/title/mojangstudios.png");
        putFull(forward, reverse, "textures/environment/sun.png", "textures/environment/celestial/sun.png");

        this.forward = Collections.unmodifiableMap(forward);
        this.reverse = Collections.unmodifiableMap(reverse);
    }

    /**
     * @return the modern target paths aliased to {@code sourcePath}, or an
     *         empty array when the source is not an expansion source
     */
    public String[] targetsFor(String sourcePath) {
        List<String> targets = forward.get(sourcePath);
        return targets == null ? new String[0] : targets.toArray(new String[0]);
    }

    /**
     * @return the source path copied for {@code targetPath}, or {@code null}
     *         when the target is not an expansion target
     */
    public String sourceForTarget(String targetPath) {
        return reverse.get(targetPath);
    }

    /**
     * @return every expansion target under {@code directoryPath} (any depth),
     *         regardless of whether its source exists
     */
    public List<String> targetsUnder(String directoryPath) {
        String prefix = directoryPath.isEmpty() ? "" : directoryPath + "/";
        List<String> out = new ArrayList<>();
        for (String target : reverse.keySet()) {
            if (target.startsWith(prefix)) {
                out.add(target);
            }
        }
        return out;
    }

    /** Armor-relative source: registered under both 1.8 spellings. */
    private static void putArmor(Map<String, List<String>> forward, Map<String, String> reverse,
            String relativeSource, String... relativeTargets) {
        String[] targets = Arrays.stream(relativeTargets)
                .map(t -> "textures/entity/equipment/" + t)
                .toArray(String[]::new);
        add(forward, reverse, ARMOR_DIR + relativeSource, targets);
        add(forward, reverse, "textures/armor/" + relativeSource, targets);
    }

    private static void putFull(Map<String, List<String>> forward, Map<String, String> reverse,
            String source, String... targets) {
        add(forward, reverse, source, targets);
    }

    private static void add(Map<String, List<String>> forward, Map<String, String> reverse,
            String source, String[] targets) {
        forward.computeIfAbsent(source, k -> new ArrayList<>()).addAll(Arrays.asList(targets));
        for (String target : targets) {
            reverse.putIfAbsent(target, source);
        }
    }
}
