// Tier: 1 (pure JUnit)
package com.rfizzle.cultivation.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigMigratorTest {

    private static JsonObject parse(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    @Test
    void preVersioningFileMigratesToCurrent() {
        JsonObject raw = parse("""
                {
                  "enableSoilFertility": false,
                  "harvestDrain": 5.0
                }
                """);

        boolean migrated = ConfigMigrator.migrate(raw);

        assertTrue(migrated, "a file with no configVersion is treated as v0 and must migrate");
        assertEquals(ConfigMigrator.CURRENT_VERSION, raw.get("configVersion").getAsInt());
        // Existing fields are carried forward untouched.
        assertFalse(raw.get("enableSoilFertility").getAsBoolean());
        assertEquals(5.0, raw.get("harvestDrain").getAsDouble());
    }

    @Test
    void currentVersionFileIsUntouched() {
        JsonObject raw = parse("""
                {
                  "configVersion": %d,
                  "harvestDrain": 5.0
                }
                """.formatted(ConfigMigrator.CURRENT_VERSION));

        boolean migrated = ConfigMigrator.migrate(raw);

        assertFalse(migrated, "an already-current file must not be migrated");
        assertEquals(ConfigMigrator.CURRENT_VERSION, raw.get("configVersion").getAsInt());
        assertEquals(5.0, raw.get("harvestDrain").getAsDouble());
    }

    @Test
    void futureVersionFileIsNotDowngraded() {
        JsonObject raw = parse("""
                {
                  "configVersion": %d
                }
                """.formatted(ConfigMigrator.CURRENT_VERSION + 5));

        boolean migrated = ConfigMigrator.migrate(raw);

        assertFalse(migrated, "a file from a newer build must be left as-is, never downgraded");
        assertEquals(ConfigMigrator.CURRENT_VERSION + 5, raw.get("configVersion").getAsInt());
    }

    @Test
    void nonNumericVersionIsTreatedAsPreVersioning() {
        JsonObject raw = parse("""
                {
                  "configVersion": "one"
                }
                """);

        boolean migrated = ConfigMigrator.migrate(raw);

        assertTrue(migrated, "a non-numeric configVersion reads as v0");
        assertEquals(ConfigMigrator.CURRENT_VERSION, raw.get("configVersion").getAsInt());
    }

    @Test
    void migrationIsIdempotent() {
        JsonObject raw = parse("{ \"fertilizerDoseHarvests\": 30 }");

        assertTrue(ConfigMigrator.migrate(raw), "first pass upgrades the pre-versioning file");
        assertFalse(ConfigMigrator.migrate(raw), "second pass is a no-op — the file is now current");
        assertEquals(ConfigMigrator.CURRENT_VERSION, raw.get("configVersion").getAsInt());
        assertEquals(30, raw.get("fertilizerDoseHarvests").getAsInt());
    }
}
