package dev.heitorrosa.packconverter.convert;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses the GUI/particles/mob-effect/map-icon/moon-phase/compass-clock
 * slicing specs from the classpath {@code /mappings/*.json} files. Each file
 * is either a flat array of spec objects (gui.json) or a single spec object
 * (the others); every path inside a file is relative to that file's base
 * directory ({@code textures/gui/}, {@code textures/particle/}, {@code
 * textures/}, ...).
 *
 * <p>Predicates, metadata and delete/remove flags are ignored: every entry is
 * indexed unconditionally and out-of-bounds or empty slices are dropped at
 * slice time. Immutable after construction (thread-safe).
 */
public final class SliceSpecLoader {

    private static final String[] RESOURCES = {
        "gui.json", "particles.json", "mob_effect.json", "map_icons.json", "moon_phases.json",
        "compass_clock.json"
    };
    private static final Map<String, String> BASE_DIRS = Map.of(
            "gui.json", "textures/gui/",
            "particles.json", "textures/particle/",
            "mob_effect.json", "textures/",
            "map_icons.json", "textures/map/",
            "moon_phases.json", "textures/environment/",
            // Sources live in the legacy textures/items/ folder while the
            // targets live in the modern textures/item/ folder, so the shared
            // base is textures/ and each entry carries its own subfolder.
            "compass_clock.json", "textures/");

    private final Map<String, List<SliceTarget>> index;
    private final Map<String, SliceEntry> reverse;

    /**
     * @param specs parsed specs; later specs win on target-path collisions
     */
    public SliceSpecLoader(List<SliceSpec> specs) {
        Map<String, List<SliceTarget>> index = new HashMap<>();
        Map<String, SliceEntry> reverse = new HashMap<>();
        for (SliceSpec spec : specs) {
            index.computeIfAbsent(spec.sourcePath(), k -> new ArrayList<>()).addAll(spec.targets());
            for (SliceTarget target : spec.targets()) {
                reverse.put(target.targetPath(), new SliceEntry(spec.sourcePath(), target,
                        spec.declaredWidth(), spec.declaredHeight()));
            }
        }
        Map<String, List<SliceTarget>> frozen = new HashMap<>();
        index.forEach((k, v) -> frozen.put(k, List.copyOf(v)));
        this.index = Collections.unmodifiableMap(frozen);
        this.reverse = Collections.unmodifiableMap(new HashMap<>(reverse));
    }

    /**
     * Loads the default mapping resources from the classpath.
     *
     * @throws IllegalStateException if a resource is missing or unparseable
     */
    public static SliceSpecLoader fromDefaultResources() {
        List<SliceSpec> specs = new ArrayList<>();
        for (String resource : RESOURCES) {
            specs.addAll(parse(readResource(resource), BASE_DIRS.get(resource)));
        }
        return new SliceSpecLoader(specs);
    }

    /** Source atlas path -> targets sliced from it. */
    public Map<String, List<SliceTarget>> index() {
        return index;
    }

    /** Modern target path -> source atlas + box to slice. */
    public Map<String, SliceEntry> reverseIndex() {
        return reverse;
    }

    private static List<SliceSpec> parse(String json, String baseDir) {
        try {
            JsonElement root = JsonParser.parseString(json);
            JsonArray specs = root.isJsonArray() ? root.getAsJsonArray() : new JsonArray();
            if (!root.isJsonArray()) {
                specs.add(root);
            }
            List<SliceSpec> out = new ArrayList<>(specs.size());
            for (JsonElement specEl : specs) {
                JsonObject spec = specEl.getAsJsonObject();
                String source = baseDir + spec.get("path").getAsString();
                // The declared vanilla source size: per-spec "width"/"height"
                // (0 when absent -> the engine treats it as unscaled).
                int declaredWidth = spec.has("width") ? spec.get("width").getAsInt() : 0;
                int declaredHeight = spec.has("height") ? spec.get("height").getAsInt() : 0;
                List<SliceTarget> targets = new ArrayList<>();
                JsonArray textures = spec.getAsJsonArray("textures");
                if (textures != null) {
                    for (JsonElement texEl : textures) {
                        JsonObject tex = texEl.getAsJsonObject();
                        JsonObject box = tex.getAsJsonObject("box");
                        targets.add(new SliceTarget(
                                baseDir + tex.get("path").getAsString(),
                                box.get("x").getAsInt(),
                                box.get("y").getAsInt(),
                                box.get("width").getAsInt(),
                                box.get("height").getAsInt()));
                    }
                }
                out.add(new SliceSpec(source, declaredWidth, declaredHeight, List.copyOf(targets)));
            }
            return out;
        } catch (RuntimeException e) {
            throw new IllegalStateException("Unparseable slice mapping data", e);
        }
    }

    private static String readResource(String resource) {
        try (InputStream in = SliceSpecLoader.class.getResourceAsStream("/mappings/" + resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing mapping resource: /mappings/" + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read mapping resource: " + resource, e);
        }
    }
}
