package com.rfizzle.cultivation.network;

import com.rfizzle.cultivation.attachment.DietStore;
import com.rfizzle.cultivation.config.CultivationConfig;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

/**
 * Registers the diet-sync payload type and keeps each player's client copy
 * fresh: a full push on join and a push after every eat (from {@link
 * com.rfizzle.cultivation.diet.DietHandler}). Send-only — the client never talks
 * back, and all fatigue math stays server-authoritative.
 */
public final class DietNetworking {
    private DietNetworking() {
    }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(DietSyncS2CPayload.TYPE, DietSyncS2CPayload.CODEC);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sync(handler.getPlayer()));
    }

    /** Pushes the player's current diet snapshot; empty stacks while fatigue is disabled. */
    public static void sync(ServerPlayer player) {
        Map<ResourceLocation, Integer> stacks = CultivationConfig.get().enableDietaryFatigue
                ? DietStore.get(player).stacks()
                : Map.of();
        ServerPlayNetworking.send(player, new DietSyncS2CPayload(stacks));
    }
}
