package com.rfizzle.cultivation.api;

import com.rfizzle.cultivation.Cultivation;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fired server-side from the dietary-fatigue choke point after a food is
 * consumed and its fatigue recorded — both the generic {@code Player#eat} food
 * path and the {@code CakeBlock#eat} slice path fire it. Observation only; the
 * mod exposes no way to change the applied nutrition from here.
 *
 * <p>It does <b>not</b> fire while {@code enableDietaryFatigue} is false (no
 * fatigue is computed or recorded). Cake slices report {@code minecraft:cake}.
 * A listener that throws is caught, logged, and skipped; it can never break the
 * eat or the listeners after it.
 */
@Stable
@FunctionalInterface
public interface CultivationFoodCallback {

    /** One-shot gate so a listener that throws on every eat logs its stack trace once. */
    AtomicBoolean LISTENER_FAILURE_LOGGED = new AtomicBoolean(false);

    Event<CultivationFoodCallback> EVENT = EventFactory.createArrayBacked(CultivationFoodCallback.class,
            listeners -> (player, food, effectiveness) -> {
                for (CultivationFoodCallback listener : listeners) {
                    try {
                        listener.onFood(player, food, effectiveness);
                    } catch (VirtualMachineError e) {
                        throw e; // OOME/SOE: the JVM is gone, not the guest
                    } catch (Throwable t) {
                        // Throwable, not Exception: a listener compiled against an older
                        // signature throws Error (AbstractMethodError, NoClassDefFoundError),
                        // which an Exception catch would let escape and kill the server tick.
                        if (LISTENER_FAILURE_LOGGED.compareAndSet(false, true)) {
                            Cultivation.LOGGER.warn("A CultivationFoodCallback listener {} threw; skipping",
                                    listener.getClass().getName(), t);
                        }
                    }
                }
            });

    /**
     * @param player        the player who ate, server-side
     * @param food          the consumed food item (cake slices report {@code minecraft:cake})
     * @param effectiveness the fatigue multiplier applied to this eat, in {@code [fatigueFloor, 1.0]}
     */
    void onFood(ServerPlayer player, Item food, float effectiveness);
}
