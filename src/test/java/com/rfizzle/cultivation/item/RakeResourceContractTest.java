package com.rfizzle.cultivation.item;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the iron rake's shipped resources ({@code design/SPEC.md} §7): a missing
 * lang key renders as a raw translation string, a missing texture as the
 * placeholder, and a recipe or enchantable-tag typo silently strips the item of
 * its crafting or its enchantability — none of which a headless build catches.
 * Tier 1: pure JSON contracts, no bootstrap.
 */
class RakeResourceContractTest {
    private static final String RAKE = "iron_rake";
    private static final String RAKE_ID = "cultivation:iron_rake";

    private static JsonObject readJson(String path) {
        try (InputStream in = RakeResourceContractTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, "resource must ship: " + path);
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (Exception e) {
            throw new AssertionError("failed to read " + path, e);
        }
    }

    private static boolean resourceExists(String path) {
        return RakeResourceContractTest.class.getClassLoader().getResource(path) != null;
    }

    private static List<String> stringValues(JsonObject tag) {
        JsonArray values = tag.getAsJsonArray("values");
        return values.asList().stream().map(JsonElement::getAsString).toList();
    }

    @Test
    void langKeyIsPresentAndNonBlank() {
        JsonObject lang = readJson("assets/cultivation/lang/en_us.json");
        String key = "item.cultivation." + RAKE;
        assertTrue(lang.has(key), "the item name key must ship: " + key);
        assertFalse(lang.get(key).getAsString().isBlank(), key + " must not be blank");
    }

    @Test
    void modelIsHandheldAndResolvesToAShippedTexture() {
        JsonObject model = readJson("assets/cultivation/models/item/" + RAKE + ".json");
        assertEquals("minecraft:item/handheld", model.get("parent").getAsString(),
                "the rake must use the handheld tool model so it swings in-hand");
        String layer0 = model.getAsJsonObject("textures").get("layer0").getAsString();
        assertEquals("cultivation:item/" + RAKE, layer0, "the model must point at the mod's own sprite");
        assertTrue(resourceExists("assets/cultivation/textures/item/" + RAKE + ".png"),
                "the layer0 texture must ship: assets/cultivation/textures/item/" + RAKE + ".png");
    }

    @Test
    void shapedRecipeProducesTheRake() {
        JsonObject recipe = readJson("data/cultivation/recipe/" + RAKE + ".json");
        assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString());
        assertEquals(RAKE_ID, recipe.getAsJsonObject("result").get("id").getAsString());
        List<String> pattern = recipe.getAsJsonArray("pattern").asList().stream()
                .map(JsonElement::getAsString).toList();
        assertEquals(List.of("III", " S ", " S "), pattern, "the rake must use the §7 toothed-head pattern");
    }

    @Test
    void rakeTagHoldsTheRake() {
        List<String> values = stringValues(readJson("data/cultivation/tags/item/rakes.json"));
        assertTrue(values.contains(RAKE_ID), "#cultivation:rakes must list the iron rake: " + values);
    }

    @Test
    void rakeJoinsTheDurabilityEnchantableTag() {
        List<String> values = stringValues(readJson("data/minecraft/tags/item/enchantable/durability.json"));
        assertTrue(values.contains(RAKE_ID),
                "#minecraft:enchantable/durability must include the rake for Unbreaking/Mending: " + values);
    }
}
