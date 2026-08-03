package dev.heitorrosa.packconverter.convert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link PackMetaFixer}: converts an old pack.mcmeta "pack" object
 * into a modern one with min_format/max_format arrays.
 */
class PackMetaFixerTest {

    private final PackMetaFixer fixer = new PackMetaFixer();

    @Test
    void convertsOldPackWithDescription() {
        String out = fixer.fixPackMcmeta("{\"pack\":{\"pack_format\":2,\"description\":\"My Pack\"}}", 88, 0);

        JsonObject root = JsonParser.parseString(out).getAsJsonObject();
        JsonObject pack = root.getAsJsonObject("pack");
        assertEquals("My Pack", pack.get("description").getAsString());
        assertEquals(jsonArray(88, 0), pack.getAsJsonArray("min_format"));
        assertEquals(jsonArray(88, 0), pack.getAsJsonArray("max_format"));
    }

    @Test
    void nullInputYieldsDefaultDescription() {
        String out = fixer.fixPackMcmeta(null, 88, 0);

        JsonObject pack = JsonParser.parseString(out).getAsJsonObject().getAsJsonObject("pack");
        assertEquals("Converted pack", pack.get("description").getAsString());
        assertEquals(jsonArray(88, 0), pack.getAsJsonArray("min_format"));
        assertEquals(jsonArray(88, 0), pack.getAsJsonArray("max_format"));
    }

    @Test
    void blankInputYieldsDefaultDescription() {
        String out = fixer.fixPackMcmeta("   ", 88, 0);

        JsonObject pack = JsonParser.parseString(out).getAsJsonObject().getAsJsonObject("pack");
        assertEquals("Converted pack", pack.get("description").getAsString());
    }

    @Test
    void missingPackObjectYieldsDefaultDescription() {
        String out = fixer.fixPackMcmeta("{\"foo\": 1}", 88, 0);

        JsonObject pack = JsonParser.parseString(out).getAsJsonObject().getAsJsonObject("pack");
        assertEquals("Converted pack", pack.get("description").getAsString());
    }

    @Test
    void unparseableInputYieldsDefaultDescription() {
        String out = fixer.fixPackMcmeta("{not json", 88, 0);

        JsonObject pack = JsonParser.parseString(out).getAsJsonObject().getAsJsonObject("pack");
        assertEquals("Converted pack", pack.get("description").getAsString());
        assertEquals(jsonArray(88, 0), pack.getAsJsonArray("min_format"));
        assertEquals(jsonArray(88, 0), pack.getAsJsonArray("max_format"));
    }

    @Test
    void preservesTextComponentDescriptionObject() {
        String out = fixer.fixPackMcmeta("{\"pack\":{\"pack_format\":2,\"description\":{\"text\":\"x\",\"color\":\"red\"}}}", 88, 0);

        JsonObject desc = JsonParser.parseString(out).getAsJsonObject().getAsJsonObject("pack").getAsJsonObject("description");
        assertEquals("x", desc.get("text").getAsString());
        assertEquals("red", desc.get("color").getAsString());
    }

    @Test
    void outputIsValidJson() {
        String out = fixer.fixPackMcmeta("{\"pack\":{\"description\":\"P\"}}", 88, 0);
        assertNotNull(JsonParser.parseString(out));
        assertTrue(out.trim().startsWith("{"));
    }

    private static JsonArray jsonArray(int... values) {
        JsonArray arr = new JsonArray();
        for (int v : values) {
            arr.add(v);
        }
        return arr;
    }
}
