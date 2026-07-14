package com.rfizzle.cultivation.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rfizzle.cultivation.Cultivation;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * The single mod config, {@code config/cultivation.json} — every key from
 * {@code design/SPEC.md} §Configuration. Server keys are authoritative gameplay
 * rules; the client keys are presentation toggles honored only on the client.
 *
 * <p>The instance published by {@link #get()} is read-only after publication: its
 * fields are not volatile, so concurrent readers are only safe because a settings
 * change swaps in a fresh instance via {@link #reload()} — never mutate the live
 * instance in place.
 */
public class CultivationConfig {
    private static volatile CultivationConfig INSTANCE;

    static final Gson GSON = new GsonBuilder().setPrettyPrinting().setLenient().create();

    // A real config is a few KB; anything near this size is not a config file.
    // Refusing it before the read keeps a pathological file from ballooning the heap.
    static final long MAX_FILE_BYTES = 1024 * 1024;

    // Schema version of the on-disk file. Bumped by ConfigMigrator when the shape changes; a
    // freshly constructed config is already current. Not player-tunable — leave it out of clamp().
    public int configVersion = ConfigMigrator.CURRENT_VERSION;

    // --- Server Config ---

    // Soil fertility (§1)
    public boolean enableSoilFertility = true;
    public double harvestDrain = 3.0;
    public double rotationDrainMultiplier = 0.5;
    public double fallowRecoveryPerRandomTick = 2.0;
    public double rainRecoveryMultiplier = 2.0;
    public double boneMealFertilityRestore = 25.0;
    public double tiredThreshold = 25.0;
    public double tiredGrowthMultiplier = 0.75;
    public double exhaustedGrowthMultiplier = 0.5;
    public boolean enableNonFarmlandSoil = true;

    // Polyculture (§2)
    public boolean enablePolyculture = true;
    public double polycultureGrowthMultiplier = 1.2;
    public int polycultureMinDifferentNeighbors = 2;
    public boolean enableSnifferPolyculture = true;
    public double snifferPolycultureBonusMultiplier = 2.0;

    // Bee pollination (§2)
    public boolean enableBeePollination = true;
    public double beePollinationGrowthMultiplier = 1.1;
    public int beePollinationRange = 8;

    // Dietary fatigue (§3)
    public boolean enableDietaryFatigue = true;
    public double fatiguePerRepeat = 0.10;
    public double fatigueFloor = 0.5;
    public int fatigueResetDistinctFoods = 3;

    // Meal buffs (§4)
    public boolean enableMealBuffs = true;
    public int mealBuffDurationTicks = 2400;
    public int cakeBuffDurationTicks = 1200;
    public int snackBuffDurationTicks = 1200;

    // Enriched tilling (§5)
    public boolean enableEnrichedTilling = true;
    public int diamondHoeEnrichChance = 10;
    public int netheriteHoeEnrichChance = 15;
    public boolean enrichedSoilResistsTrampling = true;

    // Compost Fertilizer (§6)
    public boolean enableFertilizer = true;
    public boolean composterProducesFertilizer = true;
    public int fertilizerDoseHarvests = 15;

    // Scythe (§7)
    public boolean enableScytheHarvest = true;

    // Right-click harvest (§7)
    public boolean enableRightClickHarvest = true;

    // Broadcast sowing (§7)
    public boolean enableBroadcastSowing = true;

    // Villager stewardship (§8)
    public boolean enableVillagerStewardship = true;
    public boolean enableVillagerFertilizing = true;
    public double villagerFallowThreshold = 25.0;
    public double villagerReplantThreshold = 50.0;

    // --- Client Config ---

    public boolean showSoilOverlays = true;
    public int soilOverlayRenderDistance = 24;
    public boolean showFatigueTooltips = true;
    public boolean showNutritionTooltips = true;

    public void clamp() {
        CultivationConfig defaults = new CultivationConfig();
        harvestDrain = clampDouble("harvestDrain", harvestDrain, 0.0, 100.0, defaults.harvestDrain);
        rotationDrainMultiplier = clampDouble("rotationDrainMultiplier", rotationDrainMultiplier, 0.0, 1.0, defaults.rotationDrainMultiplier);
        fallowRecoveryPerRandomTick = clampDouble("fallowRecoveryPerRandomTick", fallowRecoveryPerRandomTick, 0.0, 100.0, defaults.fallowRecoveryPerRandomTick);
        rainRecoveryMultiplier = clampDouble("rainRecoveryMultiplier", rainRecoveryMultiplier, 1.0, 10.0, defaults.rainRecoveryMultiplier);
        boneMealFertilityRestore = clampDouble("boneMealFertilityRestore", boneMealFertilityRestore, 0.0, 100.0, defaults.boneMealFertilityRestore);
        tiredThreshold = clampDouble("tiredThreshold", tiredThreshold, 0.0, 100.0, defaults.tiredThreshold);
        tiredGrowthMultiplier = clampDouble("tiredGrowthMultiplier", tiredGrowthMultiplier, 0.0, 1.0, defaults.tiredGrowthMultiplier);
        exhaustedGrowthMultiplier = clampDouble("exhaustedGrowthMultiplier", exhaustedGrowthMultiplier, 0.0, 1.0, defaults.exhaustedGrowthMultiplier);
        polycultureGrowthMultiplier = clampDouble("polycultureGrowthMultiplier", polycultureGrowthMultiplier, 1.0, 5.0, defaults.polycultureGrowthMultiplier);
        polycultureMinDifferentNeighbors = clampInt("polycultureMinDifferentNeighbors", polycultureMinDifferentNeighbors, 1, 4);
        snifferPolycultureBonusMultiplier = clampDouble("snifferPolycultureBonusMultiplier", snifferPolycultureBonusMultiplier, 1.0, 5.0, defaults.snifferPolycultureBonusMultiplier);
        beePollinationGrowthMultiplier = clampDouble("beePollinationGrowthMultiplier", beePollinationGrowthMultiplier, 1.0, 5.0, defaults.beePollinationGrowthMultiplier);
        beePollinationRange = clampInt("beePollinationRange", beePollinationRange, 1, 16);
        fatiguePerRepeat = clampDouble("fatiguePerRepeat", fatiguePerRepeat, 0.0, 1.0, defaults.fatiguePerRepeat);
        fatigueFloor = clampDouble("fatigueFloor", fatigueFloor, 0.0, 1.0, defaults.fatigueFloor);
        fatigueResetDistinctFoods = clampInt("fatigueResetDistinctFoods", fatigueResetDistinctFoods, 2, 5);
        mealBuffDurationTicks = clampInt("mealBuffDurationTicks", mealBuffDurationTicks, 200, 72000);
        cakeBuffDurationTicks = clampInt("cakeBuffDurationTicks", cakeBuffDurationTicks, 200, 72000);
        snackBuffDurationTicks = clampInt("snackBuffDurationTicks", snackBuffDurationTicks, 200, 72000);
        diamondHoeEnrichChance = clampInt("diamondHoeEnrichChance", diamondHoeEnrichChance, 0, 100);
        netheriteHoeEnrichChance = clampInt("netheriteHoeEnrichChance", netheriteHoeEnrichChance, 0, 100);
        fertilizerDoseHarvests = clampInt("fertilizerDoseHarvests", fertilizerDoseHarvests, 1, 1000);
        villagerFallowThreshold = clampDouble("villagerFallowThreshold", villagerFallowThreshold, 0.0, 100.0, defaults.villagerFallowThreshold);
        villagerReplantThreshold = clampDouble("villagerReplantThreshold", villagerReplantThreshold, 0.0, 100.0, defaults.villagerReplantThreshold);
        soilOverlayRenderDistance = clampInt("soilOverlayRenderDistance", soilOverlayRenderDistance, 4, 64);
        // Replanting must resume no lower than where it stopped (SPEC §8 hysteresis); runs after
        // the range clamps so the raised value stays inside both fields' stated ranges.
        if (villagerReplantThreshold < villagerFallowThreshold) {
            Cultivation.LOGGER.warn("Config 'villagerReplantThreshold' value {} is below villagerFallowThreshold ({}); raised to {}",
                    villagerReplantThreshold, villagerFallowThreshold, villagerFallowThreshold);
            villagerReplantThreshold = villagerFallowThreshold;
        }
    }

    /**
     * Clamp {@code value} into {@code [min, max]}, logging a warning when the
     * hand-edited value was actually out of range (warn-and-clamp — a player
     * can see exactly which field their edit overrode).
     */
    private static int clampInt(String name, int value, int min, int max) {
        int clamped = Math.clamp(value, min, max);
        if (clamped != value) {
            Cultivation.LOGGER.warn("Config '{}' value {} out of range [{}, {}]; clamped to {}",
                    name, value, min, max, clamped);
        }
        return clamped;
    }

    /**
     * Double counterpart of {@link #clampInt}. NaN slides through
     * {@code Math.clamp} untouched (and Gson's lenient parse accepts a bare
     * {@code NaN} token), so it is healed to the field default before the
     * range clamp — a NaN multiplier would otherwise silently break every
     * growth roll it feeds.
     */
    private static double clampDouble(String name, double value, double min, double max, double fallback) {
        if (Double.isNaN(value)) {
            Cultivation.LOGGER.warn("Config '{}' value {} is not a number; reset to default {}",
                    name, value, fallback);
            return fallback;
        }
        double clamped = Math.clamp(value, min, max);
        if (clamped != value) {
            Cultivation.LOGGER.warn("Config '{}' value {} out of range [{}, {}]; clamped to {}",
                    name, value, min, max, clamped);
        }
        return clamped;
    }

    /**
     * Serializes this config to the JSON the server→client sync ships. The whole
     * POJO rides across so a new field reaches clients without a bespoke codec;
     * the client only ever <em>reads</em> the server-authoritative keys back
     * (client-only presentation keys stay local — see {@code ClientCultivationConfig}).
     */
    public String toSyncJson() {
        return GSON.toJson(this);
    }

    /**
     * Rebuilds a config from a {@link #toSyncJson()} blob: deserialize, then clamp
     * so a hostile or malformed payload can never seat an out-of-range rule on the
     * client. No migration runs — the sending server already carries the current
     * schema. A parse failure degrades to defaults rather than dropping the
     * connection.
     */
    public static CultivationConfig fromSyncJson(String json) {
        try {
            CultivationConfig config = GSON.fromJson(json, CultivationConfig.class);
            if (config == null) {
                config = new CultivationConfig();
            }
            config.clamp();
            return config;
        } catch (Exception e) {
            Cultivation.LOGGER.warn("Failed to parse synced config; using defaults", e);
            return new CultivationConfig();
        }
    }

    public static CultivationConfig get() {
        CultivationConfig local = INSTANCE;
        if (local == null) {
            synchronized (CultivationConfig.class) {
                local = INSTANCE;
                if (local == null) {
                    local = load();
                    INSTANCE = local;
                }
            }
        }
        return local;
    }

    /** Rebuilds the active config from disk — the seam {@code /cultivation reload} will call. */
    public static void reload() {
        synchronized (CultivationConfig.class) {
            INSTANCE = load();
        }
    }

    public void save() {
        save(configPath());
    }

    void save(Path path) {
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(tmp, GSON.toJson(this));
            try {
                Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            Cultivation.LOGGER.error("Failed to save config", e);
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException cleanup) {
                Cultivation.LOGGER.warn("Failed to delete orphan config tmp file {}", tmp, cleanup);
            }
        }
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("cultivation.json");
    }

    private static CultivationConfig load() {
        return load(configPath());
    }

    static CultivationConfig load(Path path) {
        if (!Files.exists(path)) {
            CultivationConfig defaults = new CultivationConfig();
            defaults.save(path);
            return defaults;
        }
        try {
            long size = Files.size(path);
            if (size > MAX_FILE_BYTES) {
                Cultivation.LOGGER.error("Config at {} is {} bytes (limit {}); using defaults (existing file left untouched)",
                        path, size, MAX_FILE_BYTES);
                return new CultivationConfig();
            }
            // Migrate the raw JSON before Gson so renamed/restructured fields survive the upgrade,
            // then deserialize, clamp, and persist the upgraded schema back to disk.
            String json = Files.readString(path);
            JsonElement element = JsonParser.parseString(json);
            if (element == null || !element.isJsonObject()) {
                Cultivation.LOGGER.error("Config at {} is not a JSON object; using defaults (existing file left untouched)", path);
                return new CultivationConfig();
            }
            JsonObject raw = element.getAsJsonObject();
            boolean migrated = ConfigMigrator.migrate(raw);

            CultivationConfig config = GSON.fromJson(raw, CultivationConfig.class);
            if (config == null) {
                config = new CultivationConfig();
            }
            config.clamp();
            if (migrated) {
                config.save(path);
            }
            return config;
        } catch (Exception e) {
            Cultivation.LOGGER.error("Failed to load config, using defaults (corrupted file preserved at {})", path, e);
            return new CultivationConfig();
        }
    }
}
