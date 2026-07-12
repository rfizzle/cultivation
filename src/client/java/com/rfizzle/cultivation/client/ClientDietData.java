package com.rfizzle.cultivation.client;

import com.rfizzle.cultivation.network.DietSyncS2CPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/**
 * The client's read-only copy of the owning player's dietary fatigue, refreshed
 * by {@link DietSyncS2CPayload} and read only for tooltips. Cleared on
 * disconnect so a stale snapshot never bleeds into the next server. Written on
 * the client thread from the network receiver; the {@code volatile} fields make
 * the swap visible to the tooltip render read.
 */
public final class ClientDietData {
    private static volatile Map<ResourceLocation, Integer> stacks = Map.of();
    private static volatile float fatiguePerRepeat;
    private static volatile float fatigueFloor;

    private ClientDietData() {
    }

    public static void accept(DietSyncS2CPayload payload) {
        stacks = Map.copyOf(payload.stacks());
        fatiguePerRepeat = payload.fatiguePerRepeat();
        fatigueFloor = payload.fatigueFloor();
    }

    public static void clear() {
        stacks = Map.of();
        fatiguePerRepeat = 0.0F;
        fatigueFloor = 0.0F;
    }

    public static int stackCount(ResourceLocation item) {
        return stacks.getOrDefault(item, 0);
    }

    public static float fatiguePerRepeat() {
        return fatiguePerRepeat;
    }

    public static float fatigueFloor() {
        return fatigueFloor;
    }
}
