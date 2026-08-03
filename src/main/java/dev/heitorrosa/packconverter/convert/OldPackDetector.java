package dev.heitorrosa.packconverter.convert;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import java.util.Collection;

/**
 * Decides whether a resource pack needs conversion: the pack.mcmeta is
 * missing/blank, its {@code pack} object lacks BOTH {@code min_format} and
 * {@code max_format}, or any root path is in a legacy 1.8 texture folder.
 */
public final class OldPackDetector {

    private final PathRemapper pathRemapper;

    public OldPackDetector() {
        // ponytail: legacy detection never touches the rename table; map unused.
        this.pathRemapper = new PathRemapper(null);
    }

    public boolean needsConversion(String mcmetaJson, Collection<String> rootPaths) {
        if (mcmetaJson == null || mcmetaJson.isBlank()) {
            return true;
        }
        if (rootPaths != null) {
            for (String path : rootPaths) {
                if (pathRemapper.isLegacyPath(path)) {
                    return true;
                }
            }
        }
        return lacksModernFormat(mcmetaJson);
    }

    private static boolean lacksModernFormat(String mcmetaJson) {
        try {
            JsonElement root = JsonParser.parseString(mcmetaJson);
            if (!root.isJsonObject()) {
                return true;
            }
            JsonObject pack = root.getAsJsonObject().getAsJsonObject("pack");
            if (pack == null) {
                // No "pack" object: vacuously lacks both markers.
                return true;
            }
            boolean hasMin = pack.has("min_format");
            boolean hasMax = pack.has("max_format");
            return !(hasMin || hasMax);
        } catch (JsonSyntaxException | IllegalStateException e) {
            return true;
        }
    }
}
