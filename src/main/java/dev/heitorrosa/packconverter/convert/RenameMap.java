package dev.heitorrosa.packconverter.convert;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Loads the block/item rename tables from {@code /mappings/blocks.json} and
 * {@code /mappings/items.json} and chains every version section in ascending
 * numeric order. A 1.8 name is run through each step in turn, so renames that
 * span multiple versions (e.g. {@code tall_grass -> grass -> short_grass}) are
 * resolved to the final modern name.
 */
public final class RenameMap {

    private static final String BLOCKS_RESOURCE = "/mappings/blocks.json";
    private static final String ITEMS_RESOURCE = "/mappings/items.json";

    private final List<Map<String, String>> steps;

    private RenameMap(List<Map<String, String>> steps) {
        this.steps = steps;
    }

    /**
     * Loads and chains the default mapping resources from the classpath.
     *
     * @throws IllegalStateException if a resource is missing or unparseable
     */
    public static RenameMap fromDefaultResources() {
        return fromJson(readResource(BLOCKS_RESOURCE), readResource(ITEMS_RESOURCE));
    }

    /**
     * Renames a 1.8-era name through every version step, returning the final
     * modern name, or the input unchanged when nothing matches any step.
     */
    public String rename(String name) {
        String current = name;
        for (Map<String, String> step : steps) {
            String next = step.get(current);
            if (next != null) {
                current = next;
            }
        }
        return current;
    }

    /**
     * Reverse index: final (modern) base name -> every legacy base name that
     * renames to it, deduplicated. Computed once from the chained steps; used
     * to probe a legacy pack for a resource when a direct modern-path lookup
     * misses (PathRemapper is one-way, legacy -> modern).
     */
    public Map<String, List<String>> reverseIndex() {
        Map<String, List<String>> reverse = new HashMap<>();
        for (Map<String, String> step : steps) {
            for (String legacy : step.keySet()) {
                String finalName = rename(legacy);
                List<String> names = reverse.computeIfAbsent(finalName, k -> new ArrayList<>());
                if (!names.contains(legacy)) {
                    names.add(legacy);
                }
            }
        }
        return reverse;
    }

    private static String readResource(String resource) {
        try (InputStream in = RenameMap.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing mapping resource: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read mapping resource: " + resource, e);
        }
    }

    private static RenameMap fromJson(String blocksJson, String itemsJson) {
        try {
            JsonObject blocks = JsonParser.parseString(blocksJson).getAsJsonObject();
            JsonObject items = JsonParser.parseString(itemsJson).getAsJsonObject();

            // Union of version keys ("1_13", "1_14", ...) sorted by numeric
            // component (1_20_3 sorts between 1_20 and 1_21, not after 1_21).
            TreeSet<String> versions = new TreeSet<>(VERSION_COMPARATOR);
            versions.addAll(blocks.keySet());
            versions.addAll(items.keySet());

            List<Map<String, String>> steps = new ArrayList<>(versions.size());
            for (String version : versions) {
                Map<String, String> step = new HashMap<>();
                collect(step, blocks.getAsJsonObject(version));
                collect(step, items.getAsJsonObject(version));
                steps.add(step);
            }
            return new RenameMap(steps);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Unparseable mapping data", e);
        }
    }

    private static void collect(Map<String, String> into, JsonObject section) {
        if (section == null) {
            return;
        }
        for (Map.Entry<String, JsonElement> entry : section.entrySet()) {
            into.put(entry.getKey(), entry.getValue().getAsString());
        }
    }

    private static final Comparator<String> VERSION_COMPARATOR = (a, b) -> {
        String[] ap = a.split("_");
        String[] bp = b.split("_");
        for (int i = 0; i < Math.max(ap.length, bp.length); i++) {
            int av = i < ap.length ? Integer.parseInt(ap[i]) : 0;
            int bv = i < bp.length ? Integer.parseInt(bp[i]) : 0;
            if (av != bv) {
                return Integer.compare(av, bv);
            }
        }
        return 0;
    };
}
