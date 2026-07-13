package com.rfizzle.cultivation.soil;

import com.rfizzle.cultivation.config.CultivationConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;

/**
 * The bee-pollination growth bonus ({@code design/SPEC.md} §2): a crop within
 * {@code beePollinationRange} of a populated beehive or bee nest grows at
 * {@code beePollinationGrowthMultiplier}, stacking multiplicatively with the
 * fertility band (§1) and polyculture. Hives are found through vanilla's own
 * {@code bee_home} POI index — the same lookup vanilla bee AI uses — so there is
 * no block scan, no stored state, and no sync. The bee is never read or touched:
 * the effect lands entirely on the crop, honoring the crop/animal silo boundary.
 */
public final class BeePollination {
    private BeePollination() {
    }

    /**
     * The bee-pollination multiplier for the growth roll of the crop at
     * {@code cropPos}. Exactly 1.0 when the feature is disabled or no populated
     * hive sits within range — the POI query runs only once the config gate
     * passes, keeping the common (no-bees) case a single boolean check.
     */
    public static float multiplierAt(ServerLevel level, BlockPos cropPos) {
        CultivationConfig config = CultivationConfig.get();
        if (!config.enableBeePollination) {
            return 1.0F;
        }
        boolean active = hasActiveHiveInRange(level, cropPos, config.beePollinationRange);
        return multiplier(active, config.beePollinationGrowthMultiplier);
    }

    /** Pure gate math: the configured multiplier when a hive is in range, else 1.0. */
    public static float multiplier(boolean activeHiveInRange, double configMultiplier) {
        return activeHiveInRange ? (float) configMultiplier : 1.0F;
    }

    /**
     * Whether a populated hive sits within {@code range} of {@code cropPos},
     * asked of vanilla's section-indexed {@code bee_home} POI records. A POI
     * record exists for every hive block regardless of tenancy, so occupancy is
     * confirmed against each hit's {@link BeehiveBlockEntity} — the block-entity
     * read touches only the few hives the index actually returns.
     */
    public static boolean hasActiveHiveInRange(ServerLevel level, BlockPos cropPos, int range) {
        return level.getPoiManager()
                .getInRange(holder -> holder.is(PoiTypeTags.BEE_HOME), cropPos, range, PoiManager.Occupancy.ANY)
                .anyMatch(record -> isActiveHive(level, record.getPos()));
    }

    /** A hive is active when its block entity currently houses at least one bee. */
    public static boolean isActiveHive(Level level, BlockPos hivePos) {
        return level.getBlockEntity(hivePos) instanceof BeehiveBlockEntity hive && !hive.isEmpty();
    }
}
