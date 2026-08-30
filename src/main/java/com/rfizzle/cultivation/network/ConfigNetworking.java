package com.rfizzle.cultivation.network;

import com.rfizzle.cultivation.config.CultivationConfig;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Registers the config-sync payload and keeps every client's server-authoritative
 * config copy fresh: a full push on join, and a re-broadcast after
 * {@code /cultivation reload} swaps the live config. Send-only — the client never
 * writes config back over the network; ModMenu edits go through the local file.
 */
public final class ConfigNetworking {
    private ConfigNetworking() {
    }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(ConfigSyncPayload.TYPE, ConfigSyncPayload.CODEC);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sync(handler.getPlayer()));
    }

    /** Pushes the current server config to one player. */
    public static void sync(ServerPlayer player) {
        ServerPlayNetworking.send(player, ConfigSyncPayload.of(CultivationConfig.get()));
    }

    /** Re-broadcasts the current server config to every connected player (post-reload). */
    public static void syncAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sync(player);
        }
    }
}
