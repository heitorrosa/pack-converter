package dev.heitorrosa.packconverter.convert;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;

/**
 * Produces a modern pack.mcmeta "pack" object from an old one. The old
 * {@code description} (plain string or text-component object) is preserved
 * verbatim; {@code min_format}/{@code max_format} arrays are emitted from the
 * given format numbers.
 */
public final class PackMetaFixer {

    private static final String DEFAULT_DESCRIPTION = "Converted pack";

    /**
     * @param oldMcmetaJson the raw old pack.mcmeta contents, or {@code null}/
     *                      blank to use the default description
     * @param formatMajor   major component of the target format, e.g. 88
     * @param formatMinor   minor component of the target format, e.g. 0
     * @return a JSON string: {@code {"pack":{"description":...,"min_format":[M,m],"max_format":[M,m]}}}
     */
    public String fixPackMcmeta(String oldMcmetaJson, int formatMajor, int formatMinor) {
        JsonElement description = findDescription(oldMcmetaJson);
        if (description == null || description.isJsonNull()) {
            description = new JsonPrimitive(DEFAULT_DESCRIPTION);
        }

        JsonObject pack = new JsonObject();
        pack.add("description", description);
        pack.add("min_format", formatArray(formatMajor, formatMinor));
        pack.add("max_format", formatArray(formatMajor, formatMinor));

        JsonObject root = new JsonObject();
        root.add("pack", pack);
        return root.toString();
    }

    /**
     * @return the {@code "pack"."description"} element, or {@link JsonNull#INSTANCE}
     *         when the input is null/blank/unparseable or has no description key
     */
    private static JsonElement findDescription(String oldMcmetaJson) {
        if (oldMcmetaJson == null || oldMcmetaJson.isBlank()) {
            return JsonNull.INSTANCE;
        }
        try {
            JsonElement root = JsonParser.parseString(oldMcmetaJson);
            if (!root.isJsonObject()) {
                return JsonNull.INSTANCE;
            }
            JsonObject pack = root.getAsJsonObject().getAsJsonObject("pack");
            if (pack == null) {
                return JsonNull.INSTANCE;
            }
            JsonElement description = pack.get("description");
            return description == null ? JsonNull.INSTANCE : description;
        } catch (JsonSyntaxException | IllegalStateException e) {
            return JsonNull.INSTANCE;
        }
    }

    private static JsonArray formatArray(int major, int minor) {
        JsonArray arr = new JsonArray();
        arr.add(major);
        arr.add(minor);
        return arr;
    }
}
