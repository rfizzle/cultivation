package com.rfizzle.cultivation.meal;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier 1 resource-contract guard for the meal-buff effects: the three effect
 * names have non-blank lang entries and each effect's {@code mob_effect} icon
 * exists on the classpath, so vanilla's effect HUD never renders a raw key or a
 * missing-texture checker.
 */
class MealBuffResourcesTest {
    private static final String LANG_RESOURCE = "/assets/cultivation/lang/en_us.json";
    private static final Path LANG_SOURCE = Path.of("src/main/resources/assets/cultivation/lang/en_us.json");

    private static JsonObject lang() {
        try (InputStream in = MealBuffResourcesTest.class.getResourceAsStream(LANG_RESOURCE)) {
            String json = in != null
                    ? new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    : Files.readString(LANG_SOURCE, StandardCharsets.UTF_8);
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (IOException e) {
            throw new AssertionError("could not load en_us.json", e);
        }
    }

    @Test
    void everyEffectHasANonBlankName() {
        JsonObject lang = lang();
        for (String effect : new String[] {"nimble", "diligent", "sated"}) {
            String key = "effect.cultivation." + effect;
            assertTrue(lang.has(key), "missing lang key " + key);
            assertTrue(!lang.get(key).getAsString().isBlank(), key + " is blank");
        }
    }

    @Test
    void everyEffectIconIsShipped() {
        for (String effect : new String[] {"nimble", "diligent", "sated"}) {
            String path = "/assets/cultivation/textures/mob_effect/" + effect + ".png";
            assertNotNull(MealBuffResourcesTest.class.getResourceAsStream(path),
                    "missing effect texture " + path);
        }
    }
}
