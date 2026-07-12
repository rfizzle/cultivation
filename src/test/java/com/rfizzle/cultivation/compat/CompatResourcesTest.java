package com.rfizzle.cultivation.compat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rfizzle.cultivation.config.ConfigFields;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-1 resource-contract guard for the integration surfaces (mc-config,
 * mc-compat): every {@code config.cultivation.*} label and tooltip the ModMenu
 * screen renders has a non-blank lang entry, so a player never sees a raw
 * translation key in the settings GUI. Derives the config keys from
 * {@link ConfigFields#ALL}, so a new field's keys are checked automatically.
 */
class CompatResourcesTest {
    private static final String LANG_RESOURCE = "/assets/cultivation/lang/en_us.json";
    private static final Path LANG_SOURCE = Path.of("src/main/resources/assets/cultivation/lang/en_us.json");

    private static JsonObject lang() {
        try (InputStream in = CompatResourcesTest.class.getResourceAsStream(LANG_RESOURCE)) {
            String json = in != null
                    ? new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    : Files.readString(LANG_SOURCE, StandardCharsets.UTF_8);
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (IOException e) {
            throw new AssertionError("could not load en_us.json", e);
        }
    }

    private static void assertNonBlank(JsonObject lang, String key) {
        assertTrue(lang.has(key), "missing lang key " + key);
        assertFalse(lang.get(key).getAsString().isBlank(), key + " is blank");
    }

    @Test
    void configScreenTitleAndCategoriesAreLocalized() {
        JsonObject lang = lang();
        assertNonBlank(lang, "config.cultivation.title");
        for (String category : ConfigFields.CATEGORIES) {
            assertNonBlank(lang, "config.cultivation.category." + category);
        }
    }

    @Test
    void everyConfigFieldHasALabelAndTooltip() {
        JsonObject lang = lang();
        for (ConfigFields.Spec spec : ConfigFields.ALL) {
            assertNonBlank(lang, spec.labelKey());
            assertNonBlank(lang, spec.tooltipKey());
        }
    }

    /** Keys the Jade/WTHIT soil and crop tooltip formatters emit (mc-probe-tooltips). */
    private static final String[] PROBE_TOOLTIP_KEYS = {
            "tooltip.cultivation.soil.fertility",
            "tooltip.cultivation.soil.band.rich",
            "tooltip.cultivation.soil.band.fair",
            "tooltip.cultivation.soil.band.tired",
            "tooltip.cultivation.soil.band.exhausted",
            "tooltip.cultivation.soil.enriched",
            "tooltip.cultivation.soil.fertilizer",
            "tooltip.cultivation.soil.crop",
            "tooltip.cultivation.crop.growth",
            "tooltip.cultivation.crop.polyculture",
    };

    @Test
    void everyProbeTooltipKeyIsLocalized() {
        JsonObject lang = lang();
        for (String key : PROBE_TOOLTIP_KEYS) {
            assertNonBlank(lang, key);
        }
    }
}
