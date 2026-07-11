package com.rfizzle.cultivation.api;

import com.rfizzle.cultivation.Cultivation;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Fired server-side from the harvest choke point whenever a supported mature
 * crop over farmland is destroyed with drops — player breaks, pistons, water,
 * explosions, and every future harvest path (scythe sweeps, villager harvests)
 * ride the same seam. It fires after Cultivation's own soil work (fertility
 * drain, the exhausted yield clamp, and future enriched/Fertilizer bonuses), and
 * fires regardless of {@code enableSoilFertility} — the toggle freezes the soil
 * system, not the harvest seam.
 *
 * <p>The {@code drops} list is mutable and is the sanctioned mutation point for
 * siblings and third parties (e.g. quality-produce injection). A listener that
 * throws is caught, logged, and skipped; it can never break the harvest or the
 * listeners after it.
 */
@Stable
@FunctionalInterface
public interface CultivationHarvestCallback {
    Event<CultivationHarvestCallback> EVENT = EventFactory.createArrayBacked(CultivationHarvestCallback.class,
            listeners -> (level, pos, crop, drops, harvester) -> {
                for (CultivationHarvestCallback listener : listeners) {
                    try {
                        listener.onHarvest(level, pos, crop, drops, harvester);
                    } catch (Throwable t) {
                        Cultivation.LOGGER.error("CultivationHarvestCallback listener threw; skipping it", t);
                    }
                }
            });

    /**
     * @param level     the server level the harvest happened in
     * @param pos       the destroyed crop block's position (not the farmland)
     * @param crop      the destroyed crop's block state
     * @param drops     the resolved drops, after the exhausted clamp — mutable
     * @param harvester the destroying entity (player, piston-less null, explosion source), when known
     */
    void onHarvest(ServerLevel level, BlockPos pos, BlockState crop, List<ItemStack> drops, @Nullable Entity harvester);
}
