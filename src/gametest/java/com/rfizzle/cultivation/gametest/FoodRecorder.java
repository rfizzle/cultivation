package com.rfizzle.cultivation.gametest;

import com.rfizzle.cultivation.api.CultivationFoodCallback;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Test-side {@link CultivationFoodCallback} listener. Fabric events cannot be
 * unregistered, so it registers once for the whole gametest server and records
 * every fired eat; tests filter by player UUID (each mock player is unique) so
 * concurrent or sequential tests never see each other's eats.
 */
final class FoodRecorder {
    record Recorded(UUID player, ResourceLocation item, float effectiveness) {
    }

    private static final AtomicBoolean REGISTERED = new AtomicBoolean();
    static final List<Recorded> RECORDS = new CopyOnWriteArrayList<>();

    private FoodRecorder() {
    }

    static void ensureRegistered() {
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }
        CultivationFoodCallback.EVENT.register((player, food, effectiveness) ->
                RECORDS.add(new Recorded(player.getUUID(), BuiltInRegistries.ITEM.getKey(food), effectiveness)));
    }

    static List<Recorded> forPlayer(UUID player) {
        return RECORDS.stream().filter(r -> r.player().equals(player)).toList();
    }
}
