package com.rfizzle.cultivation.advancement;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-1 resource-contract guard for the Husbandry-tab advancements
 * ({@code design/SPEC.md} §10): each shipped JSON parses, parents into the
 * vanilla Husbandry tab, references its {@code cultivation:} trigger, keeps
 * telemetry off, and has non-blank title/description lang entries — so a broken
 * file or a missing key is caught here rather than at world load.
 */
class AdvancementResourcesTest {
    private static final Path ADVANCEMENT_DIR =
            Path.of("src/main/resources/data/cultivation/advancement");
    private static final Path LANG_SOURCE =
            Path.of("src/main/resources/assets/cultivation/lang/en_us.json");

    // The five advancement ids paired with the criterion key each file declares.
    private static final String[][] ADVANCEMENTS = {
            {"balanced_table", "reset"},
            {"long_term_investment", "dosed"},
            {"reap_what_you_sow", "swept"},
            {"full_broadcast", "sown"},
            {"old_growth", "reaped"},
    };

    private static JsonObject read(Path path) {
        try {
            return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new AssertionError("could not read " + path, e);
        }
    }

    @Test
    void everyAdvancementIsWellFormedAndWiredToItsTrigger() {
        for (String[] entry : ADVANCEMENTS) {
            String id = entry[0];
            String criterionKey = entry[1];
            JsonObject json = read(ADVANCEMENT_DIR.resolve(id + ".json"));

            assertEquals("minecraft:husbandry/root", json.get("parent").getAsString(),
                    id + " must parent into the vanilla Husbandry tab");
            assertTrue(!json.get("sends_telemetry_event").getAsBoolean(),
                    id + " must keep sends_telemetry_event off");

            JsonObject criteria = json.getAsJsonObject("criteria");
            assertTrue(criteria.has(criterionKey), id + " must declare the '" + criterionKey + "' criterion");
            assertEquals("cultivation:" + id,
                    criteria.getAsJsonObject(criterionKey).get("trigger").getAsString(),
                    id + " must fire from the cultivation:" + id + " trigger");

            JsonObject display = json.getAsJsonObject("display");
            assertEquals("advancements.cultivation." + id + ".title",
                    display.getAsJsonObject("title").get("translate").getAsString(),
                    id + " title must use its namespaced lang key");
            assertEquals("advancements.cultivation." + id + ".description",
                    display.getAsJsonObject("description").get("translate").getAsString(),
                    id + " description must use its namespaced lang key");
        }
    }

    @Test
    void everyAdvancementHasNonBlankTitleAndDescription() {
        JsonObject lang = read(LANG_SOURCE);
        for (String[] entry : ADVANCEMENTS) {
            String id = entry[0];
            for (String suffix : new String[]{"title", "description"}) {
                String key = "advancements.cultivation." + id + "." + suffix;
                assertTrue(lang.has(key), "missing lang key " + key);
                assertTrue(!lang.get(key).getAsString().isBlank(), key + " is blank");
            }
        }
    }
}
