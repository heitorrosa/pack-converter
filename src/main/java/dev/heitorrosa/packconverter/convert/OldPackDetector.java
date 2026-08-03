package dev.heitorrosa.packconverter.convert;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

/**
 * Decides whether a resource pack needs conversion: the pack.mcmeta is
 * missing/blank, or its {@code pack} object lacks BOTH {@code min_format} and
 * {@code max_format}.
 */
public final class OldPackDetector {

    public boolean needsConversion(String mcmetaJson) {
        if (mcmetaJson == null || mcmetaJson.isBlank()) {
            return true;
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
