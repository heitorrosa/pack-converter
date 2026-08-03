package dev.heitorrosa.packconverter.convert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ModelRewriter}: rewrites texture references inside
 * model/blockstate JSON files.
 */
class ModelRewriterTest {

    private static ModelRewriter rewriter;
    private static PathRemapper remapper;

    @BeforeAll
    static void setUp() {
        rewriter = new ModelRewriter();
        remapper = new PathRemapper(RenameMap.fromDefaultResources());
    }

    @Test
    void rewritesBlockTextureReference() {
        String out = rewriter.rewriteModelJson("{\"textures\":{\"all\":\"block/planks_oak\"}}", remapper);
        assertEquals("block/oak_planks", textures(out).get("all").getAsString());
    }

    @Test
    void rewritesItemLayerTextureReference() {
        String out = rewriter.rewriteModelJson("{\"textures\":{\"layer0\":\"items/apple_golden\"}}", remapper);
        assertEquals("item/golden_apple", textures(out).get("layer0").getAsString());
    }

    @Test
    void rewritesBareTextureName() {
        String out = rewriter.rewriteModelJson("{\"textures\":{\"all\":\"planks_oak\"}}", remapper);
        assertEquals("oak_planks", textures(out).get("all").getAsString());
    }

    @Test
    void rewritesNamespacedTextureReference() {
        String out = rewriter.rewriteModelJson("{\"textures\":{\"all\":\"minecraft:textures/blocks/planks_oak.png\"}}", remapper);
        assertEquals("minecraft:textures/block/oak_planks.png", textures(out).get("all").getAsString());
    }

    @Test
    void parentValueIsRemappedAndUnchangedWhenNoMatch() {
        String out = rewriter.rewriteModelJson("{\"parent\":\"block/cube_all\",\"textures\":{}}", remapper);
        assertEquals("block/cube_all", JsonParser.parseString(out).getAsJsonObject().get("parent").getAsString());
    }

    @Test
    void preservesOtherKeys() {
        String out = rewriter.rewriteModelJson("{\"ambientocclusion\":false,\"textures\":{\"all\":\"block/planks_oak\"}}", remapper);
        JsonObject root = JsonParser.parseString(out).getAsJsonObject();
        assertEquals(false, root.get("ambientocclusion").getAsBoolean());
        assertEquals("block/oak_planks", textures(out).get("all").getAsString());
    }

    @Test
    void remapsVariantBlockStateModelKeys() {
        String blockState = "{\"variants\":{\"normal\":{\"model\":\"block/planks_oak\"}}}";
        String out = rewriter.rewriteBlockStateJson(blockState, remapper);
        JsonObject root = JsonParser.parseString(out).getAsJsonObject();
        assertEquals("block/oak_planks",
                root.getAsJsonObject("variants").getAsJsonObject("normal").get("model").getAsString());
    }

    @Test
    void remapsMultipartBlockStateModelKeys() {
        String blockState = "{\"multipart\":[{\"when\":{\"north\":\"true\"},\"apply\":{\"model\":\"block/planks_oak\"}}]}";
        String out = rewriter.rewriteBlockStateJson(blockState, remapper);
        JsonObject apply = JsonParser.parseString(out).getAsJsonObject()
                .getAsJsonArray("multipart").get(0).getAsJsonObject().getAsJsonObject("apply");
        assertEquals("block/oak_planks", apply.get("model").getAsString());
    }

    @Test
    void blockStateNonModelKeysPreserved() {
        String blockState = "{\"variants\":{\"facing=north\":{\"model\":\"block/planks_oak\",\"y\":90,\"uvlock\":true}}}";
        String out = rewriter.rewriteBlockStateJson(blockState, remapper);
        JsonObject variant = JsonParser.parseString(out).getAsJsonObject()
                .getAsJsonObject("variants").getAsJsonObject("facing=north");
        assertEquals("block/oak_planks", variant.get("model").getAsString());
        assertEquals(90, variant.get("y").getAsInt());
        assertEquals(true, variant.get("uvlock").getAsBoolean());
    }

    @Test
    void invalidModelJsonThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> rewriter.rewriteModelJson("{not json", remapper));
    }

    @Test
    void invalidBlockStateJsonThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> rewriter.rewriteBlockStateJson("{not json", remapper));
    }

    @Test
    void emptyTextureObjectIsHandled() {
        String out = rewriter.rewriteModelJson("{\"textures\":{}}", remapper);
        assertTrue(JsonParser.parseString(out).getAsJsonObject().has("textures"));
    }

    private static JsonObject textures(String modelJson) {
        return JsonParser.parseString(modelJson).getAsJsonObject().getAsJsonObject("textures");
    }
}
