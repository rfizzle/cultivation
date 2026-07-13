package com.rfizzle.cultivation.command;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-1 resource-contract guard for the {@code /cultivation} command output:
 * every {@code command.cultivation.*} key the tree emits has a non-blank lang
 * entry, so a client never sees a raw translation key in command feedback.
 */
class CommandResourcesTest {
    private static final String LANG_RESOURCE = "/assets/cultivation/lang/en_us.json";
    private static final Path LANG_SOURCE = Path.of("src/main/resources/assets/cultivation/lang/en_us.json");

    private static final String[] KEYS = {
            "command.cultivation.soil.report",
            "command.cultivation.soil.crop",
            "command.cultivation.soil.enriched",
            "command.cultivation.soil.fertilizer",
            "command.cultivation.soil.band.rich",
            "command.cultivation.soil.band.fair",
            "command.cultivation.soil.band.tired",
            "command.cultivation.soil.band.exhausted",
            "command.cultivation.soil.not_soil",
            "command.cultivation.soil.set",
            "command.cultivation.field.report",
            "command.cultivation.field.counts",
            "command.cultivation.field.crops",
            "command.cultivation.field.crops.none",
            "command.cultivation.diet.none",
            "command.cultivation.diet.fatigue",
            "command.cultivation.diet.entry",
            "command.cultivation.diet.recent",
            "command.cultivation.diet.reset",
            "command.cultivation.reload",
            "command.cultivation.reload_failed",
    };

    private static JsonObject lang() {
        try (InputStream in = CommandResourcesTest.class.getResourceAsStream(LANG_RESOURCE)) {
            String json = in != null
                    ? new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    : Files.readString(LANG_SOURCE, StandardCharsets.UTF_8);
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (IOException e) {
            throw new AssertionError("could not load en_us.json", e);
        }
    }

    @Test
    void everyCommandKeyHasANonBlankEntry() {
        JsonObject lang = lang();
        for (String key : KEYS) {
            assertTrue(lang.has(key), "missing lang key " + key);
            assertTrue(!lang.get(key).getAsString().isBlank(), key + " is blank");
        }
    }
}
