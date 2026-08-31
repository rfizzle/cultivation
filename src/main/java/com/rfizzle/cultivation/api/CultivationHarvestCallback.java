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
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fired server-side from the harvest choke point whenever a supported crop is
 * harvested with drops — a farmland crop, nether wart on soul sand, or a sweet
 * berry bush — across every path that reaps one: player breaks, pistons, water,
 * explosions, scythe sweeps, and villager harvests. It also fires on a
 * sweet-berry <em>pick</em>, which pops berries <em>without</em> destroying the
 * bush (the bush persists and resets to age 1) — so a listener must not assume
 * the block at {@code pos} is gone. It fires after Cultivation's own soil work
 * (fertility drain, the exhausted yield clamp, and the enriched/Fertilizer
 * bonuses), and fires regardless of {@code enableSoilFertility} — the toggle
 * freezes the soil system, not the harvest seam.
 *
 * <p>The {@code drops} list is mutable and is the sanctioned mutation point for
 * siblings and third parties (e.g. quality-produce injection). A listener that
 * throws is caught, logged, and skipped; it can never break the harvest or the
 * listeners after it.
 */
@Stable
@FunctionalInterface
public interface CultivationHarvestCallback {

    /**
     * One-shot gate so a listener that throws on every harvest logs its stack
     * trace once — this event fires once per harvested crop block, so an
     * ungated log turns one explosion in a farm into a hundred traces in a tick.
     */
    AtomicBoolean LISTENER_FAILURE_LOGGED = new AtomicBoolean(false);

    Event<CultivationHarvestCallback> EVENT = EventFactory.createArrayBacked(CultivationHarvestCallback.class,
            listeners -> (level, pos, crop, drops, harvester) -> {
                for (CultivationHarvestCallback listener : listeners) {
                    try {
                        listener.onHarvest(level, pos, crop, drops, harvester);
                    } catch (VirtualMachineError e) {
                        throw e; // OOME/SOE: the JVM is gone, not the guest
                    } catch (Throwable t) {
                        // Throwable, not Exception: a listener compiled against an older
                        // signature throws Error (AbstractMethodError, NoClassDefFoundError),
                        // which an Exception catch would let escape and kill the server tick.
                        if (LISTENER_FAILURE_LOGGED.compareAndSet(false, true)) {
                            Cultivation.LOGGER.warn("A CultivationHarvestCallback listener {} threw; skipping",
                                    listener.getClass().getName(), t);
                        }
                    }
                }
            });

    /**
     * @param level     the server level the harvest happened in
     * @param pos       the harvested crop block's position (not the ground below); on a
     *                  sweet-berry pick the bush at this position survives, on every other path it is destroyed
     * @param crop      the harvested crop's block state (its pre-pick state on a berry pick)
     * @param drops     the resolved drops, after the exhausted clamp — mutable
     * @param harvester the destroying entity (player, piston-less null, explosion source), when known
     */
    void onHarvest(ServerLevel level, BlockPos pos, BlockState crop, List<ItemStack> drops, @Nullable Entity harvester);
}
