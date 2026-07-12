package com.rfizzle.cultivation.item;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the three scythes' shipped resources ({@code design/SPEC.md} §7): a
 * missing lang key renders as a raw translation string, a missing texture as the
 * purple-black placeholder, and a recipe or enchantable-tag typo silently strips
 * the item of its crafting or its enchantability — none of which a headless build
 * would otherwise catch. Tier 1: pure JSON contracts, no bootstrap.
 */
class ScytheAssetsTest {
    private static final List<String> SCYTHES = List.of("iron_scythe", "diamond_scythe", "netherite_scythe");
    private static final List<String> SCYTHE_IDS =
            List.of("cultivation:iron_scythe", "cultivation:diamond_scythe", "cultivation:netherite_scythe");

    static final class Scythes implements ArgumentsProvider {
        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
            return SCYTHES.stream().map(Arguments::of);
        }
    }

    private static JsonObject readJson(String path) {
        try (InputStream in = ScytheAssetsTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, "resource must ship: " + path);
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (Exception e) {
            throw new AssertionError("failed to read " + path, e);
        }
    }

    private static boolean resourceExists(String path) {
        return ScytheAssetsTest.class.getClassLoader().getResource(path) != null;
    }

    private static List<String> stringValues(JsonObject tag) {
        JsonArray values = tag.getAsJsonArray("values");
        return values.asList().stream().map(JsonElement::getAsString).toList();
    }

    @ParameterizedTest
    @ArgumentsSource(Scythes.class)
    void langKeyIsPresentAndNonBlank(String scythe) {
        JsonObject lang = readJson("assets/cultivation/lang/en_us.json");
        String key = "item.cultivation." + scythe;
        assertTrue(lang.has(key), "the item name key must ship: " + key);
        assertFalse(lang.get(key).getAsString().isBlank(), key + " must not be blank");
    }

    @ParameterizedTest
    @ArgumentsSource(Scythes.class)
    void modelIsHandheldAndResolvesToAShippedTexture(String scythe) {
        JsonObject model = readJson("assets/cultivation/models/item/" + scythe + ".json");
        assertEquals("minecraft:item/handheld", model.get("parent").getAsString(),
                scythe + " must use the handheld tool model so it swings in-hand");
        String layer0 = model.getAsJsonObject("textures").get("layer0").getAsString();
        assertEquals("cultivation:item/" + scythe, layer0, "the model must point at the mod's own sprite");
        assertTrue(resourceExists("assets/cultivation/textures/item/" + scythe + ".png"),
                "the layer0 texture must ship: assets/cultivation/textures/item/" + scythe + ".png");
    }

    @Test
    void shapedRecipesProduceTheScythes() {
        for (String metal : List.of("iron", "diamond")) {
            JsonObject recipe = readJson("data/cultivation/recipe/" + metal + "_scythe.json");
            assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString());
            assertEquals("cultivation:" + metal + "_scythe",
                    recipe.getAsJsonObject("result").get("id").getAsString());
            List<String> pattern = recipe.getAsJsonArray("pattern").asList().stream()
                    .map(JsonElement::getAsString).toList();
            assertEquals(List.of(" II", "IS ", " S "), pattern, metal + " scythe must use the §7 curved-blade pattern");
        }
    }

    @Test
    void netheriteScytheUpgradesViaSmithing() {
        JsonObject recipe = readJson("data/cultivation/recipe/netherite_scythe.json");
        assertEquals("minecraft:smithing_transform", recipe.get("type").getAsString());
        assertEquals("cultivation:diamond_scythe", recipe.getAsJsonObject("base").get("item").getAsString());
        assertEquals("minecraft:netherite_ingot", recipe.getAsJsonObject("addition").get("item").getAsString());
        assertEquals("cultivation:netherite_scythe", recipe.getAsJsonObject("result").get("id").getAsString());
    }

    @Test
    void scytheTagHoldsAllThree() {
        List<String> values = stringValues(readJson("data/cultivation/tags/item/scythes.json"));
        assertTrue(values.containsAll(SCYTHE_IDS), "#cultivation:scythes must list all three scythes: " + values);
    }

    @Test
    void scythesJoinTheEnchantableTags() {
        for (String tag : List.of("durability", "mining")) {
            List<String> values = stringValues(readJson("data/minecraft/tags/item/enchantable/" + tag + ".json"));
            assertTrue(values.containsAll(SCYTHE_IDS),
                    "#minecraft:enchantable/" + tag + " must include the scythes: " + values);
        }
    }
}
