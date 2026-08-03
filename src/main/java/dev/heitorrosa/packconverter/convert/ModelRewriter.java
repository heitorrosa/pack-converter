package dev.heitorrosa.packconverter.convert;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import java.util.Map;

/**
 * Rewrites texture references inside model/blockstate JSON. Every value inside
 * the {@code textures} object and the {@code parent} value is run through
 * {@link PathRemapper}; all other keys are preserved. Blockstate files are
 * passed through untouched (model file paths were not flattened).
 */
public final class ModelRewriter {

    /**
     * @param modelJson a model JSON document (e.g. {@code
     *                  {"textures":{"all":"block/planks_oak"}}})
     * @throws IllegalArgumentException if the JSON is invalid or not an object
     */
    public String rewriteModelJson(String modelJson, PathRemapper remapper) {
        JsonObject root = parseObject(modelJson);

        if (root.has("parent") && root.get("parent").isJsonPrimitive()) {
            JsonPrimitive parent = root.getAsJsonPrimitive("parent");
            if (parent.isString()) {
                root.add("parent", new JsonPrimitive(remapper.remapPath(parent.getAsString())));
            }
        }

        if (root.has("textures") && root.get("textures").isJsonObject()) {
            JsonObject textures = root.getAsJsonObject("textures");
            for (Map.Entry<String, JsonElement> entry : textures.entrySet()) {
                JsonElement value = entry.getValue();
                if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                    textures.add(entry.getKey(),
                            new JsonPrimitive(remapper.remapPath(value.getAsString())));
                }
            }
        }

        return root.toString();
    }

    /**
     * Returns the blockstate JSON unchanged (model file paths were not
     * flattened in modern blockstates), after validating that it parses.
     *
     * @throws IllegalArgumentException if the JSON is invalid or not an object
     */
    public String rewriteBlockStateJson(String blockStateJson, PathRemapper remapper) {
        parseObject(blockStateJson);
        return blockStateJson;
    }

    private static JsonObject parseObject(String json) {
        try {
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (JsonSyntaxException | IllegalStateException e) {
            throw new IllegalArgumentException("Invalid model JSON", e);
        }
    }
}
