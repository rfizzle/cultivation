// Tier: 1 (pure JUnit)
package com.rfizzle.cultivation.config;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CultivationConfigTest {
    private static final Gson GSON = CultivationConfig.GSON;

    // Every key the spec's Configuration section names, server then client.
    private static final List<String> SPEC_KEYS = List.of(
            "enableSoilFertility", "harvestDrain", "rotationDrainMultiplier",
            "fallowRecoveryPerRandomTick", "rainRecoveryMultiplier", "boneMealFertilityRestore",
            "tiredThreshold", "tiredGrowthMultiplier", "exhaustedGrowthMultiplier",
            "enablePolyculture", "polycultureGrowthMultiplier", "polycultureMinDifferentNeighbors",
            "enableDietaryFatigue", "fatiguePerRepeat", "fatigueFloor", "fatigueResetDistinctFoods",
            "enableMealBuffs", "mealBuffDurationTicks", "cakeBuffDurationTicks",
            "enableEnrichedTilling", "diamondHoeEnrichChance", "netheriteHoeEnrichChance",
            "enableFertilizer", "composterProducesFertilizer", "fertilizerDoseHarvests",
            "enableScytheHarvest",
            "enableVillagerStewardship", "enableVillagerFertilizing",
            "villagerFallowThreshold", "villagerReplantThreshold",
            "showSoilOverlays", "soilOverlayRenderDistance", "showFatigueTooltips");

    @Test
    void defaultValuesMatchTheSpecTable() {
        CultivationConfig config = new CultivationConfig();

        assertEquals(1, config.configVersion);
        assertTrue(config.enableSoilFertility);
        assertEquals(3.0, config.harvestDrain);
        assertEquals(0.5, config.rotationDrainMultiplier);
        assertEquals(2.0, config.fallowRecoveryPerRandomTick);
        assertEquals(2.0, config.rainRecoveryMultiplier);
        assertEquals(25.0, config.boneMealFertilityRestore);
        assertEquals(25.0, config.tiredThreshold);
        assertEquals(0.75, config.tiredGrowthMultiplier);
        assertEquals(0.5, config.exhaustedGrowthMultiplier);
        assertTrue(config.enablePolyculture);
        assertEquals(1.2, config.polycultureGrowthMultiplier);
        assertEquals(2, config.polycultureMinDifferentNeighbors);
        assertTrue(config.enableDietaryFatigue);
        assertEquals(0.10, config.fatiguePerRepeat);
        assertEquals(0.5, config.fatigueFloor);
        assertEquals(3, config.fatigueResetDistinctFoods);
        assertTrue(config.enableMealBuffs);
        assertEquals(2400, config.mealBuffDurationTicks);
        assertEquals(1200, config.cakeBuffDurationTicks);
        assertTrue(config.enableEnrichedTilling);
        assertEquals(10, config.diamondHoeEnrichChance);
        assertEquals(15, config.netheriteHoeEnrichChance);
        assertTrue(config.enableFertilizer);
        assertTrue(config.composterProducesFertilizer);
        assertEquals(15, config.fertilizerDoseHarvests);
        assertTrue(config.enableScytheHarvest);
        assertTrue(config.enableVillagerStewardship);
        assertTrue(config.enableVillagerFertilizing);
        assertEquals(25.0, config.villagerFallowThreshold);
        assertEquals(50.0, config.villagerReplantThreshold);
        assertTrue(config.showSoilOverlays);
        assertEquals(24, config.soilOverlayRenderDistance);
        assertTrue(config.showFatigueTooltips);
    }

    @Test
    void firstLaunchWritesEveryKeyWithDefaults(@TempDir Path dir) throws IOException {
        Path path = dir.resolve("cultivation.json");

        CultivationConfig config = CultivationConfig.load(path);

        assertTrue(Files.exists(path), "first launch must write the config file");
        JsonObject written = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        assertEquals(1, written.get("configVersion").getAsInt());
        for (String key : SPEC_KEYS) {
            assertTrue(written.has(key), "first-launch file missing spec key: " + key);
        }
        assertEquals(3.0, config.harvestDrain);
    }

    @Test
    void missingKeysGetDefaults() {
        String json = """
                {
                  "configVersion": 1,
                  "enableSoilFertility": false,
                  "fertilizerDoseHarvests": 30
                }
                """;

        CultivationConfig config = GSON.fromJson(json, CultivationConfig.class);

        assertFalse(config.enableSoilFertility);
        assertEquals(30, config.fertilizerDoseHarvests);

        CultivationConfig defaults = new CultivationConfig();
        assertEquals(defaults.harvestDrain, config.harvestDrain);
        assertEquals(defaults.polycultureGrowthMultiplier, config.polycultureGrowthMultiplier);
        assertEquals(defaults.mealBuffDurationTicks, config.mealBuffDurationTicks);
        assertTrue(config.showSoilOverlays);
        assertEquals(defaults.soilOverlayRenderDistance, config.soilOverlayRenderDistance);
        assertTrue(config.showFatigueTooltips);
    }

    @Test
    void unknownKeysAreIgnored(@TempDir Path dir) throws IOException {
        Path path = dir.resolve("cultivation.json");
        Files.writeString(path, """
                {
                  "configVersion": 1,
                  "someRetiredKey": 17,
                  "harvestDrain": 5.0
                }
                """);

        CultivationConfig config = CultivationConfig.load(path);

        assertEquals(5.0, config.harvestDrain);
        assertTrue(config.enableSoilFertility);
    }

    @Test
    void valuesBelowMinimumAreClamped() {
        CultivationConfig config = new CultivationConfig();
        config.harvestDrain = -1.0;
        config.rotationDrainMultiplier = -0.1;
        config.fallowRecoveryPerRandomTick = -2.0;
        config.rainRecoveryMultiplier = 0.5;
        config.boneMealFertilityRestore = -25.0;
        config.tiredThreshold = -1.0;
        config.tiredGrowthMultiplier = -0.75;
        config.exhaustedGrowthMultiplier = -0.5;
        config.polycultureGrowthMultiplier = 0.9;
        config.polycultureMinDifferentNeighbors = 0;
        config.fatiguePerRepeat = -0.10;
        config.fatigueFloor = -0.5;
        config.fatigueResetDistinctFoods = 1;
        config.mealBuffDurationTicks = 199;
        config.cakeBuffDurationTicks = 0;
        config.diamondHoeEnrichChance = -10;
        config.netheriteHoeEnrichChance = -15;
        config.fertilizerDoseHarvests = 0;
        config.villagerFallowThreshold = -25.0;
        config.villagerReplantThreshold = -50.0;
        config.soilOverlayRenderDistance = 3;

        config.clamp();

        assertEquals(0.0, config.harvestDrain);
        assertEquals(0.0, config.rotationDrainMultiplier);
        assertEquals(0.0, config.fallowRecoveryPerRandomTick);
        assertEquals(1.0, config.rainRecoveryMultiplier);
        assertEquals(0.0, config.boneMealFertilityRestore);
        assertEquals(0.0, config.tiredThreshold);
        assertEquals(0.0, config.tiredGrowthMultiplier);
        assertEquals(0.0, config.exhaustedGrowthMultiplier);
        assertEquals(1.0, config.polycultureGrowthMultiplier);
        assertEquals(1, config.polycultureMinDifferentNeighbors);
        assertEquals(0.0, config.fatiguePerRepeat);
        assertEquals(0.0, config.fatigueFloor);
        assertEquals(2, config.fatigueResetDistinctFoods);
        assertEquals(200, config.mealBuffDurationTicks);
        assertEquals(200, config.cakeBuffDurationTicks);
        assertEquals(0, config.diamondHoeEnrichChance);
        assertEquals(0, config.netheriteHoeEnrichChance);
        assertEquals(1, config.fertilizerDoseHarvests);
        assertEquals(0.0, config.villagerFallowThreshold);
        // Range-clamped to 0, and 0 >= villagerFallowThreshold (0), so no cross-field raise.
        assertEquals(0.0, config.villagerReplantThreshold);
        assertEquals(4, config.soilOverlayRenderDistance);
    }

    @Test
    void valuesAboveMaximumAreClamped() {
        CultivationConfig config = new CultivationConfig();
        config.harvestDrain = 101.0;
        config.rotationDrainMultiplier = 1.1;
        config.fallowRecoveryPerRandomTick = 102.0;
        config.rainRecoveryMultiplier = 10.5;
        config.boneMealFertilityRestore = 125.0;
        config.tiredThreshold = 101.0;
        config.tiredGrowthMultiplier = 1.75;
        config.exhaustedGrowthMultiplier = 1.5;
        config.polycultureGrowthMultiplier = 5.2;
        config.polycultureMinDifferentNeighbors = 5;
        config.fatiguePerRepeat = 1.10;
        config.fatigueFloor = 1.5;
        config.fatigueResetDistinctFoods = 6;
        config.mealBuffDurationTicks = 72001;
        config.cakeBuffDurationTicks = 100000;
        config.diamondHoeEnrichChance = 110;
        config.netheriteHoeEnrichChance = 115;
        config.fertilizerDoseHarvests = 1001;
        config.villagerFallowThreshold = 125.0;
        config.villagerReplantThreshold = 150.0;
        config.soilOverlayRenderDistance = 65;

        config.clamp();

        assertEquals(100.0, config.harvestDrain);
        assertEquals(1.0, config.rotationDrainMultiplier);
        assertEquals(100.0, config.fallowRecoveryPerRandomTick);
        assertEquals(10.0, config.rainRecoveryMultiplier);
        assertEquals(100.0, config.boneMealFertilityRestore);
        assertEquals(100.0, config.tiredThreshold);
        assertEquals(1.0, config.tiredGrowthMultiplier);
        assertEquals(1.0, config.exhaustedGrowthMultiplier);
        assertEquals(5.0, config.polycultureGrowthMultiplier);
        assertEquals(4, config.polycultureMinDifferentNeighbors);
        assertEquals(1.0, config.fatiguePerRepeat);
        assertEquals(1.0, config.fatigueFloor);
        assertEquals(5, config.fatigueResetDistinctFoods);
        assertEquals(72000, config.mealBuffDurationTicks);
        assertEquals(72000, config.cakeBuffDurationTicks);
        assertEquals(100, config.diamondHoeEnrichChance);
        assertEquals(100, config.netheriteHoeEnrichChance);
        assertEquals(1000, config.fertilizerDoseHarvests);
        assertEquals(100.0, config.villagerFallowThreshold);
        assertEquals(100.0, config.villagerReplantThreshold);
        assertEquals(64, config.soilOverlayRenderDistance);
    }

    @Test
    void boundaryValuesPassUnclamped() {
        CultivationConfig low = new CultivationConfig();
        low.harvestDrain = 0.0;
        low.rotationDrainMultiplier = 0.0;
        low.fallowRecoveryPerRandomTick = 0.0;
        low.rainRecoveryMultiplier = 1.0;
        low.boneMealFertilityRestore = 0.0;
        low.tiredThreshold = 0.0;
        low.tiredGrowthMultiplier = 0.0;
        low.exhaustedGrowthMultiplier = 0.0;
        low.polycultureGrowthMultiplier = 1.0;
        low.polycultureMinDifferentNeighbors = 1;
        low.fatiguePerRepeat = 0.0;
        low.fatigueFloor = 0.0;
        low.fatigueResetDistinctFoods = 2;
        low.mealBuffDurationTicks = 200;
        low.cakeBuffDurationTicks = 200;
        low.diamondHoeEnrichChance = 0;
        low.netheriteHoeEnrichChance = 0;
        low.fertilizerDoseHarvests = 1;
        low.villagerFallowThreshold = 0.0;
        low.villagerReplantThreshold = 0.0;
        low.soilOverlayRenderDistance = 4;

        low.clamp();

        assertEquals(0.0, low.harvestDrain);
        assertEquals(0.0, low.rotationDrainMultiplier);
        assertEquals(0.0, low.fallowRecoveryPerRandomTick);
        assertEquals(1.0, low.rainRecoveryMultiplier);
        assertEquals(0.0, low.boneMealFertilityRestore);
        assertEquals(0.0, low.tiredThreshold);
        assertEquals(0.0, low.tiredGrowthMultiplier);
        assertEquals(0.0, low.exhaustedGrowthMultiplier);
        assertEquals(1.0, low.polycultureGrowthMultiplier);
        assertEquals(1, low.polycultureMinDifferentNeighbors);
        assertEquals(0.0, low.fatiguePerRepeat);
        assertEquals(0.0, low.fatigueFloor);
        assertEquals(2, low.fatigueResetDistinctFoods);
        assertEquals(200, low.mealBuffDurationTicks);
        assertEquals(200, low.cakeBuffDurationTicks);
        assertEquals(0, low.diamondHoeEnrichChance);
        assertEquals(0, low.netheriteHoeEnrichChance);
        assertEquals(1, low.fertilizerDoseHarvests);
        assertEquals(0.0, low.villagerFallowThreshold);
        assertEquals(0.0, low.villagerReplantThreshold);
        assertEquals(4, low.soilOverlayRenderDistance);

        CultivationConfig high = new CultivationConfig();
        high.harvestDrain = 100.0;
        high.rotationDrainMultiplier = 1.0;
        high.fallowRecoveryPerRandomTick = 100.0;
        high.rainRecoveryMultiplier = 10.0;
        high.boneMealFertilityRestore = 100.0;
        high.tiredThreshold = 100.0;
        high.tiredGrowthMultiplier = 1.0;
        high.exhaustedGrowthMultiplier = 1.0;
        high.polycultureGrowthMultiplier = 5.0;
        high.polycultureMinDifferentNeighbors = 4;
        high.fatiguePerRepeat = 1.0;
        high.fatigueFloor = 1.0;
        high.fatigueResetDistinctFoods = 5;
        high.mealBuffDurationTicks = 72000;
        high.cakeBuffDurationTicks = 72000;
        high.diamondHoeEnrichChance = 100;
        high.netheriteHoeEnrichChance = 100;
        high.fertilizerDoseHarvests = 1000;
        high.villagerFallowThreshold = 100.0;
        high.villagerReplantThreshold = 100.0;
        high.soilOverlayRenderDistance = 64;

        high.clamp();

        assertEquals(100.0, high.harvestDrain);
        assertEquals(1.0, high.rotationDrainMultiplier);
        assertEquals(100.0, high.fallowRecoveryPerRandomTick);
        assertEquals(10.0, high.rainRecoveryMultiplier);
        assertEquals(100.0, high.boneMealFertilityRestore);
        assertEquals(100.0, high.tiredThreshold);
        assertEquals(1.0, high.tiredGrowthMultiplier);
        assertEquals(1.0, high.exhaustedGrowthMultiplier);
        assertEquals(5.0, high.polycultureGrowthMultiplier);
        assertEquals(4, high.polycultureMinDifferentNeighbors);
        assertEquals(1.0, high.fatiguePerRepeat);
        assertEquals(1.0, high.fatigueFloor);
        assertEquals(5, high.fatigueResetDistinctFoods);
        assertEquals(72000, high.mealBuffDurationTicks);
        assertEquals(72000, high.cakeBuffDurationTicks);
        assertEquals(100, high.diamondHoeEnrichChance);
        assertEquals(100, high.netheriteHoeEnrichChance);
        assertEquals(1000, high.fertilizerDoseHarvests);
        assertEquals(100.0, high.villagerFallowThreshold);
        assertEquals(100.0, high.villagerReplantThreshold);
        assertEquals(64, high.soilOverlayRenderDistance);
    }

    @Test
    void nanValuesResetToTheirDefaults() {
        CultivationConfig config = new CultivationConfig();
        config.polycultureGrowthMultiplier = Double.NaN;
        config.tiredGrowthMultiplier = Double.NaN;
        config.villagerReplantThreshold = Double.NaN;

        config.clamp();

        CultivationConfig defaults = new CultivationConfig();
        assertEquals(defaults.polycultureGrowthMultiplier, config.polycultureGrowthMultiplier);
        assertEquals(defaults.tiredGrowthMultiplier, config.tiredGrowthMultiplier);
        assertEquals(defaults.villagerReplantThreshold, config.villagerReplantThreshold);
    }

    @Test
    void nanValuesInFileResetToDefaultsOnLoad(@TempDir Path dir) throws IOException {
        Path path = dir.resolve("cultivation.json");
        // Gson's lenient parse accepts a bare NaN token, so a hand-edited file
        // can deliver one; it must never reach a growth roll.
        Files.writeString(path, """
                {
                  "configVersion": 1,
                  "polycultureGrowthMultiplier": NaN,
                  "harvestDrain": 5.0
                }
                """);

        CultivationConfig config = CultivationConfig.load(path);

        assertEquals(new CultivationConfig().polycultureGrowthMultiplier, config.polycultureGrowthMultiplier);
        assertEquals(5.0, config.harvestDrain, "healthy keys in the same file must load normally");
    }

    @Test
    void replantThresholdIsRaisedToTheFallowThreshold() {
        CultivationConfig config = new CultivationConfig();
        config.villagerFallowThreshold = 60.0;
        config.villagerReplantThreshold = 40.0;

        config.clamp();

        assertEquals(60.0, config.villagerReplantThreshold);
    }

    @Test
    void replantThresholdEqualToFallowThresholdIsUntouched() {
        CultivationConfig config = new CultivationConfig();
        config.villagerFallowThreshold = 50.0;
        config.villagerReplantThreshold = 50.0;

        config.clamp();

        assertEquals(50.0, config.villagerReplantThreshold);
    }

    @Test
    void replantCrossClampAppliesAfterRangeClamp() {
        CultivationConfig config = new CultivationConfig();
        // Fallow clamps 150 -> 100 first, so replant rises to 100, not 150.
        config.villagerFallowThreshold = 150.0;
        config.villagerReplantThreshold = 40.0;

        config.clamp();

        assertEquals(100.0, config.villagerFallowThreshold);
        assertEquals(100.0, config.villagerReplantThreshold);
    }

    @Test
    void corruptFileFallsBackToDefaultsAndIsLeftUntouched(@TempDir Path dir) throws IOException {
        Path path = dir.resolve("cultivation.json");
        String corrupt = "{ this is not json";
        Files.writeString(path, corrupt);

        CultivationConfig config = CultivationConfig.load(path);

        CultivationConfig defaults = new CultivationConfig();
        assertEquals(defaults.harvestDrain, config.harvestDrain);
        assertEquals(defaults.enableSoilFertility, config.enableSoilFertility);
        assertEquals(corrupt, Files.readString(path), "a corrupt file must never be modified on disk");
    }

    @Test
    void nonObjectJsonFallsBackToDefaultsAndIsLeftUntouched(@TempDir Path dir) throws IOException {
        Path path = dir.resolve("cultivation.json");
        String nonObject = "[1, 2, 3]";
        Files.writeString(path, nonObject);

        CultivationConfig config = CultivationConfig.load(path);

        assertEquals(new CultivationConfig().fertilizerDoseHarvests, config.fertilizerDoseHarvests);
        assertEquals(nonObject, Files.readString(path), "a non-object file must never be modified on disk");
    }

    @Test
    void oversizedFileFallsBackToDefaultsAndIsLeftUntouched(@TempDir Path dir) throws IOException {
        Path path = dir.resolve("cultivation.json");
        String oversized = "{\"harvestDrain\": 5.0, \"padding\": \""
                + "x".repeat((int) CultivationConfig.MAX_FILE_BYTES) + "\"}";
        Files.writeString(path, oversized);

        CultivationConfig config = CultivationConfig.load(path);

        assertEquals(new CultivationConfig().harvestDrain, config.harvestDrain,
                "an oversized file must load as full defaults, not be parsed");
        assertEquals(oversized, Files.readString(path), "an oversized file must never be modified on disk");
    }

    @Test
    void outOfRangeValuesInFileAreClampedOnLoad(@TempDir Path dir) throws IOException {
        Path path = dir.resolve("cultivation.json");
        Files.writeString(path, """
                {
                  "configVersion": 1,
                  "harvestDrain": 9999.0,
                  "fatigueFloor": -2.0,
                  "villagerFallowThreshold": 80.0,
                  "villagerReplantThreshold": 10.0
                }
                """);

        CultivationConfig config = CultivationConfig.load(path);

        assertEquals(100.0, config.harvestDrain);
        assertEquals(0.0, config.fatigueFloor);
        assertEquals(80.0, config.villagerReplantThreshold, "replant threshold must load clamped up to the fallow threshold");
    }

    @Test
    void saveThenLoadRoundTripsAndLeavesNoTmpFile(@TempDir Path dir) throws IOException {
        Path path = dir.resolve("cultivation.json");
        CultivationConfig original = new CultivationConfig();
        original.enableSoilFertility = false;
        original.harvestDrain = 6.0;
        original.polycultureGrowthMultiplier = 1.5;
        original.fertilizerDoseHarvests = 30;
        original.showFatigueTooltips = false;

        original.save(path);
        CultivationConfig restored = CultivationConfig.load(path);

        assertFalse(restored.enableSoilFertility);
        assertEquals(6.0, restored.harvestDrain);
        assertEquals(1.5, restored.polycultureGrowthMultiplier);
        assertEquals(30, restored.fertilizerDoseHarvests);
        assertFalse(restored.showFatigueTooltips);
        assertFalse(Files.exists(dir.resolve("cultivation.json.tmp")), "atomic save must not leave a tmp file");
    }

    @Test
    void versionlessFileIsStampedAndPersistedOnLoad(@TempDir Path dir) throws IOException {
        Path path = dir.resolve("cultivation.json");
        Files.writeString(path, """
                {
                  "harvestDrain": 5.0
                }
                """);

        CultivationConfig config = CultivationConfig.load(path);

        assertEquals(5.0, config.harvestDrain);
        JsonObject written = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        assertEquals(1, written.get("configVersion").getAsInt(), "the migrated schema must be persisted back");
        assertEquals(5.0, written.get("harvestDrain").getAsDouble(), "existing tuning must be carried forward");
    }
}
