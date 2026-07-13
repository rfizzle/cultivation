package com.rfizzle.cultivation.config;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * The declarative table every tunable {@link CultivationConfig} field, in screen
 * order — the single source the ModMenu/Cloth screen iterates to build its
 * entries and the resource test walks to prove every field has a localized label
 * and tooltip. Keeping the table in the {@code main} source set (no Cloth or
 * client imports) is what makes the "screen covers every field" contract
 * unit-testable: a new config field without a matching {@link Spec} here fails
 * {@code ConfigFieldsTest}, and a {@link Spec} without a lang key fails
 * {@code CompatResourcesTest}.
 *
 * <p>Field names are the Java camelCase identifiers; the {@code config.cultivation.*}
 * lang keys are their snake_case form (SPEC §Localization, e.g. {@code harvestDrain}
 * → {@code config.cultivation.harvest_drain}). {@code configVersion} is deliberately
 * absent — it is schema bookkeeping, not player-tunable.
 */
public final class ConfigFields {
    /** The value kind a {@link Spec} edits; picks the Cloth entry builder in the screen. */
    public enum Kind { BOOL, INT, DOUBLE }

    /**
     * One editable config field. {@code min}/{@code max} mirror the ranges
     * {@link CultivationConfig#clamp()} enforces so the screen widget refuses an
     * out-of-range value before {@code clamp()} even runs; they are ignored for
     * {@link Kind#BOOL}.
     */
    public record Spec(
            String field,
            String category,
            Kind kind,
            double min,
            double max,
            Function<CultivationConfig, Object> getter,
            BiConsumer<CultivationConfig, Object> setter) {

        /** The {@code config.cultivation.<snake>} label key for this field. */
        public String labelKey() {
            return "config.cultivation." + snakeCase(field);
        }

        /** The {@code config.cultivation.<snake>.tooltip} description key for this field. */
        public String tooltipKey() {
            return labelKey() + ".tooltip";
        }

        /** The {@code config.cultivation.category.<category>} tab-title key. */
        public String categoryKey() {
            return "config.cultivation.category." + category;
        }
    }

    // Category suffixes, in screen tab order.
    public static final String CAT_SOIL = "soil";
    public static final String CAT_POLYCULTURE = "polyculture";
    public static final String CAT_DIET = "diet";
    public static final String CAT_MEALS = "meals";
    public static final String CAT_TILLING = "tilling";
    public static final String CAT_FERTILIZER = "fertilizer";
    public static final String CAT_SCYTHE = "scythe";
    public static final String CAT_VILLAGER = "villager";
    public static final String CAT_CLIENT = "client";

    /** Every category suffix, in the order the screen renders its tabs. */
    public static final List<String> CATEGORIES = List.of(
            CAT_SOIL, CAT_POLYCULTURE, CAT_DIET, CAT_MEALS, CAT_TILLING,
            CAT_FERTILIZER, CAT_SCYTHE, CAT_VILLAGER, CAT_CLIENT);

    public static final List<Spec> ALL = List.of(
            // Soil fertility (§1)
            bool("enableSoilFertility", CAT_SOIL, c -> c.enableSoilFertility, (c, v) -> c.enableSoilFertility = v),
            dbl("harvestDrain", CAT_SOIL, 0.0, 100.0, c -> c.harvestDrain, (c, v) -> c.harvestDrain = v),
            dbl("rotationDrainMultiplier", CAT_SOIL, 0.0, 1.0, c -> c.rotationDrainMultiplier, (c, v) -> c.rotationDrainMultiplier = v),
            dbl("fallowRecoveryPerRandomTick", CAT_SOIL, 0.0, 100.0, c -> c.fallowRecoveryPerRandomTick, (c, v) -> c.fallowRecoveryPerRandomTick = v),
            dbl("rainRecoveryMultiplier", CAT_SOIL, 1.0, 10.0, c -> c.rainRecoveryMultiplier, (c, v) -> c.rainRecoveryMultiplier = v),
            dbl("boneMealFertilityRestore", CAT_SOIL, 0.0, 100.0, c -> c.boneMealFertilityRestore, (c, v) -> c.boneMealFertilityRestore = v),
            dbl("tiredThreshold", CAT_SOIL, 0.0, 100.0, c -> c.tiredThreshold, (c, v) -> c.tiredThreshold = v),
            dbl("tiredGrowthMultiplier", CAT_SOIL, 0.0, 1.0, c -> c.tiredGrowthMultiplier, (c, v) -> c.tiredGrowthMultiplier = v),
            dbl("exhaustedGrowthMultiplier", CAT_SOIL, 0.0, 1.0, c -> c.exhaustedGrowthMultiplier, (c, v) -> c.exhaustedGrowthMultiplier = v),

            // Polyculture (§2)
            bool("enablePolyculture", CAT_POLYCULTURE, c -> c.enablePolyculture, (c, v) -> c.enablePolyculture = v),
            dbl("polycultureGrowthMultiplier", CAT_POLYCULTURE, 1.0, 5.0, c -> c.polycultureGrowthMultiplier, (c, v) -> c.polycultureGrowthMultiplier = v),
            intg("polycultureMinDifferentNeighbors", CAT_POLYCULTURE, 1, 4, c -> c.polycultureMinDifferentNeighbors, (c, v) -> c.polycultureMinDifferentNeighbors = v),
            bool("enableBeePollination", CAT_POLYCULTURE, c -> c.enableBeePollination, (c, v) -> c.enableBeePollination = v),
            dbl("beePollinationGrowthMultiplier", CAT_POLYCULTURE, 1.0, 5.0, c -> c.beePollinationGrowthMultiplier, (c, v) -> c.beePollinationGrowthMultiplier = v),
            intg("beePollinationRange", CAT_POLYCULTURE, 1, 16, c -> c.beePollinationRange, (c, v) -> c.beePollinationRange = v),

            // Dietary fatigue (§3)
            bool("enableDietaryFatigue", CAT_DIET, c -> c.enableDietaryFatigue, (c, v) -> c.enableDietaryFatigue = v),
            dbl("fatiguePerRepeat", CAT_DIET, 0.0, 1.0, c -> c.fatiguePerRepeat, (c, v) -> c.fatiguePerRepeat = v),
            dbl("fatigueFloor", CAT_DIET, 0.0, 1.0, c -> c.fatigueFloor, (c, v) -> c.fatigueFloor = v),
            intg("fatigueResetDistinctFoods", CAT_DIET, 2, 5, c -> c.fatigueResetDistinctFoods, (c, v) -> c.fatigueResetDistinctFoods = v),

            // Meal buffs (§4)
            bool("enableMealBuffs", CAT_MEALS, c -> c.enableMealBuffs, (c, v) -> c.enableMealBuffs = v),
            intg("mealBuffDurationTicks", CAT_MEALS, 200, 72000, c -> c.mealBuffDurationTicks, (c, v) -> c.mealBuffDurationTicks = v),
            intg("cakeBuffDurationTicks", CAT_MEALS, 200, 72000, c -> c.cakeBuffDurationTicks, (c, v) -> c.cakeBuffDurationTicks = v),
            intg("snackBuffDurationTicks", CAT_MEALS, 200, 72000, c -> c.snackBuffDurationTicks, (c, v) -> c.snackBuffDurationTicks = v),

            // Enriched tilling (§5)
            bool("enableEnrichedTilling", CAT_TILLING, c -> c.enableEnrichedTilling, (c, v) -> c.enableEnrichedTilling = v),
            intg("diamondHoeEnrichChance", CAT_TILLING, 0, 100, c -> c.diamondHoeEnrichChance, (c, v) -> c.diamondHoeEnrichChance = v),
            intg("netheriteHoeEnrichChance", CAT_TILLING, 0, 100, c -> c.netheriteHoeEnrichChance, (c, v) -> c.netheriteHoeEnrichChance = v),

            // Compost Fertilizer (§6)
            bool("enableFertilizer", CAT_FERTILIZER, c -> c.enableFertilizer, (c, v) -> c.enableFertilizer = v),
            bool("composterProducesFertilizer", CAT_FERTILIZER, c -> c.composterProducesFertilizer, (c, v) -> c.composterProducesFertilizer = v),
            intg("fertilizerDoseHarvests", CAT_FERTILIZER, 1, 1000, c -> c.fertilizerDoseHarvests, (c, v) -> c.fertilizerDoseHarvests = v),

            // Scythe (§7)
            bool("enableScytheHarvest", CAT_SCYTHE, c -> c.enableScytheHarvest, (c, v) -> c.enableScytheHarvest = v),

            // Villager stewardship (§8)
            bool("enableVillagerStewardship", CAT_VILLAGER, c -> c.enableVillagerStewardship, (c, v) -> c.enableVillagerStewardship = v),
            bool("enableVillagerFertilizing", CAT_VILLAGER, c -> c.enableVillagerFertilizing, (c, v) -> c.enableVillagerFertilizing = v),
            dbl("villagerFallowThreshold", CAT_VILLAGER, 0.0, 100.0, c -> c.villagerFallowThreshold, (c, v) -> c.villagerFallowThreshold = v),
            dbl("villagerReplantThreshold", CAT_VILLAGER, 0.0, 100.0, c -> c.villagerReplantThreshold, (c, v) -> c.villagerReplantThreshold = v),

            // Client presentation
            bool("showSoilOverlays", CAT_CLIENT, c -> c.showSoilOverlays, (c, v) -> c.showSoilOverlays = v),
            intg("soilOverlayRenderDistance", CAT_CLIENT, 4, 64, c -> c.soilOverlayRenderDistance, (c, v) -> c.soilOverlayRenderDistance = v),
            bool("showFatigueTooltips", CAT_CLIENT, c -> c.showFatigueTooltips, (c, v) -> c.showFatigueTooltips = v));

    private ConfigFields() {
    }

    /** camelCase → snake_case, matching the SPEC §Localization key convention. */
    public static String snakeCase(String camel) {
        StringBuilder out = new StringBuilder(camel.length() + 4);
        for (int i = 0; i < camel.length(); i++) {
            char ch = camel.charAt(i);
            if (Character.isUpperCase(ch)) {
                out.append('_').append(Character.toLowerCase(ch));
            } else {
                out.append(ch);
            }
        }
        return out.toString();
    }

    private static Spec bool(String field, String category,
            Function<CultivationConfig, Boolean> getter, BiConsumer<CultivationConfig, Boolean> setter) {
        return new Spec(field, category, Kind.BOOL, 0, 0,
                c -> getter.apply(c), (c, v) -> setter.accept(c, (Boolean) v));
    }

    private static Spec intg(String field, String category, int min, int max,
            Function<CultivationConfig, Integer> getter, BiConsumer<CultivationConfig, Integer> setter) {
        return new Spec(field, category, Kind.INT, min, max,
                c -> getter.apply(c), (c, v) -> setter.accept(c, (Integer) v));
    }

    private static Spec dbl(String field, String category, double min, double max,
            Function<CultivationConfig, Double> getter, BiConsumer<CultivationConfig, Double> setter) {
        return new Spec(field, category, Kind.DOUBLE, min, max,
                c -> getter.apply(c), (c, v) -> setter.accept(c, (Double) v));
    }
}
