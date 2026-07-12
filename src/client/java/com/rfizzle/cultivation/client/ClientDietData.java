package com.rfizzle.cultivation.client;

import com.rfizzle.cultivation.network.DietSyncS2CPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/**
 * The client's read-only copy of the owning player's dietary fatigue, refreshed
 * by {@link DietSyncS2CPayload} and read only for tooltips. The whole state is
 * one immutable {@link Snapshot} swapped through a single {@code volatile}
 * reference, so a tooltip read on the render thread can never see a torn mix of
 * old and new fields. Cleared on disconnect so a stale snapshot never bleeds
 * into the next server.
 */
public final class ClientDietData {
    /** An atomic, immutable view of the synced diet state. */
    public record Snapshot(Map<ResourceLocation, Integer> stacks, float fatiguePerRepeat, float fatigueFloor) {
        static final Snapshot EMPTY = new Snapshot(Map.of(), 0.0F, 0.0F);

        public boolean isEmpty() {
            return stacks.isEmpty();
        }

        public int stackCount(ResourceLocation item) {
            return stacks.getOrDefault(item, 0);
        }
    }

    private static volatile Snapshot snapshot = Snapshot.EMPTY;

    private ClientDietData() {
    }

    public static void accept(DietSyncS2CPayload payload) {
        snapshot = new Snapshot(Map.copyOf(payload.stacks()), payload.fatiguePerRepeat(), payload.fatigueFloor());
    }

    public static void clear() {
        snapshot = Snapshot.EMPTY;
    }

    public static Snapshot snapshot() {
        return snapshot;
    }
}
