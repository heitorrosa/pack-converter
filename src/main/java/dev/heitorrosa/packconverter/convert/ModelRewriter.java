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
 * {@link PathRemapper}, as is every {@code "model"} value inside blockstate
 * files; all other keys are preserved.
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
     * Remaps every string under a key named {@code "model"} through
     * {@link PathRemapper}, covering {@code "variants"} entries (object and
     * array forms) and {@code "multipart"} {@code "apply"} blocks. All other
     * keys and structure are preserved; 26.2 resolves the remapped file names
     * against the model map, so an unremapped legacy reference would render as
     * MISSING_MODEL.
     *
     * @throws IllegalArgumentException if the JSON is invalid or not an object
     */
    public String rewriteBlockStateJson(String blockStateJson, PathRemapper remapper) {
        JsonObject root = parseObject(blockStateJson);
        remapModelKeys(root, remapper);
        return root.toString();
    }

    private static void remapModelKeys(JsonObject obj, PathRemapper remapper) {
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            JsonElement value = entry.getValue();
            if ("model".equals(entry.getKey()) && value.isJsonPrimitive()
                    && value.getAsJsonPrimitive().isString()) {
                obj.add(entry.getKey(), new JsonPrimitive(remapper.remapPath(value.getAsString())));
            } else if (value.isJsonObject()) {
                remapModelKeys(value.getAsJsonObject(), remapper);
            } else if (value.isJsonArray()) {
                for (JsonElement element : value.getAsJsonArray()) {
                    if (element.isJsonObject()) {
                        remapModelKeys(element.getAsJsonObject(), remapper);
                    }
                }
            }
        }
    }

    private static JsonObject parseObject(String json) {
        try {
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (JsonSyntaxException | IllegalStateException e) {
            throw new IllegalArgumentException("Invalid model JSON", e);
        }
    }
}
