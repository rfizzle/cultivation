package com.rfizzle.cultivation.item;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the Fertilizer item's shipped client resources — a missing lang key
 * renders as a raw translation string, a missing texture as the purple-black
 * "missing texture" placeholder. Neither surfaces in a headless build otherwise.
 */
class FertilizerResourceContractTest {
    private static JsonObject readJson(String path) {
        try (InputStream in = FertilizerResourceContractTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, "resource must ship: " + path);
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (Exception e) {
            throw new AssertionError("failed to read " + path, e);
        }
    }

    private static boolean resourceExists(String path) {
        return FertilizerResourceContractTest.class.getClassLoader().getResource(path) != null;
    }

    @Test
    void langKeyIsPresent() {
        JsonObject lang = readJson("assets/cultivation/lang/en_us.json");
        assertTrue(lang.has("item.cultivation.fertilizer"), "the item name key must ship");
        assertEquals("Fertilizer", lang.get("item.cultivation.fertilizer").getAsString());
    }

    @Test
    void itemModelResolvesToAShippedTexture() {
        JsonObject model = readJson("assets/cultivation/models/item/fertilizer.json");
        String layer0 = model.getAsJsonObject("textures").get("layer0").getAsString();
        assertEquals("cultivation:item/fertilizer", layer0, "the model must point at the mod's own sprite");
        assertTrue(resourceExists("assets/cultivation/textures/item/fertilizer.png"),
                "the layer0 texture must ship at assets/cultivation/textures/item/fertilizer.png");
    }
}
