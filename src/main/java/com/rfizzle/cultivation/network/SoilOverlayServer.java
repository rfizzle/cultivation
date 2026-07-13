package com.rfizzle.cultivation.network;

import com.rfizzle.cultivation.attachment.CultivationAttachments;
import com.rfizzle.cultivation.attachment.SoilData;
import com.rfizzle.cultivation.attachment.SoilStore;
import com.rfizzle.cultivation.attachment.SoilStores;
import com.rfizzle.cultivation.config.CultivationConfig;
import com.rfizzle.cultivation.soil.SoilOverlayFlags;
import com.rfizzle.cultivation.soil.SupportedCrops;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-side soil overlay tracking ({@code design/SPEC.md} §1): builds a chunk's
 * deviating-position set for the pull response, and pushes single-position deltas
 * to the players tracking a chunk when a position's overlay state changes.
 *
 * <p>All entry points run on the server thread — every soil write flows through
 * {@link SoilStores#update} (server-thread callers only), and the farmland-removal
 * seam is a server-side {@code onRemove} inject — so {@link ServerPlayNetworking#send}
 * and {@link PlayerLookup#tracking} are safe to call directly. Overlays are a pure
 * projection of {@link SoilData}; nothing here is persisted.
 */
public final class SoilOverlayServer {
    private SoilOverlayServer() {
    }

    /**
     * Every visually deviating farmland position in a loaded chunk, or an empty
     * list when soil is disabled, the chunk is unloaded, or nothing deviates.
     */
    public static List<SoilBandsS2CPayload.Entry> collectChunkEntries(ServerLevel level, ChunkPos chunkPos) {
        CultivationConfig config = CultivationConfig.get();
        if (!config.enableSoilFertility) {
            return List.of();
        }
        LevelChunk chunk = level.getChunkSource().getChunkNow(chunkPos.x, chunkPos.z);
        if (chunk == null) {
            return List.of();
        }
        SoilStore store = chunk.getAttached(CultivationAttachments.SOIL);
        if (store == null) {
            return List.of();
        }
        List<SoilBandsS2CPayload.Entry> entries = new ArrayList<>();
        store.forEach((data, key) -> {
            BlockPos pos = new BlockPos(
                    chunkPos.getMinBlockX() + SoilStore.unpackX(key),
                    SoilStore.unpackY(key),
                    chunkPos.getMinBlockZ() + SoilStore.unpackZ(key));
            if (!SupportedCrops.isTrackedSoilGround(
                    level.getBlockState(pos), level.getBlockState(pos.above()), config.enableNonFarmlandSoil)) {
                return;
            }
            byte flags = SoilOverlayFlags.computeFlags(data, config.tiredThreshold);
            if (SoilOverlayFlags.isDeviating(flags)) {
                entries.add(new SoilBandsS2CPayload.Entry(key, flags));
            }
        });
        return entries;
    }

    /**
     * Pushes a delta when a write changed a soil position's client-visible overlay.
     * Called from the soil write choke point with the pre- and post-write state.
     * No-op when soil is disabled, the position is not soil (farmland removal is
     * handled by {@link #notifyFarmlandRemoved}), or the visible representation is
     * unchanged. A second-wave ground harvested by the break (nether wart) writes
     * its drain after the crop is already gone, so no delta fires; {@link
     * #collectChunkEntries} keys on the current crop above, so that position's
     * overlay reappears on a chunk-load pull only once wart is replanted there.
     */
    public static void notifyFlagChange(ServerLevel level, BlockPos pos, SoilData before, SoilData after) {
        CultivationConfig config = CultivationConfig.get();
        if (!config.enableSoilFertility) {
            return;
        }
        if (!SupportedCrops.isTrackedSoilGround(
                level.getBlockState(pos), level.getBlockState(pos.above()), config.enableNonFarmlandSoil)) {
            return;
        }
        byte beforeFlags = SoilOverlayFlags.computeFlags(before, config.tiredThreshold);
        byte afterFlags = SoilOverlayFlags.computeFlags(after, config.tiredThreshold);
        SoilOverlayFlags.Transition transition = SoilOverlayFlags.transition(beforeFlags, afterFlags);
        if (transition != null) {
            sendDelta(level, pos, transition.present(), transition.flags());
        }
    }

    /**
     * Pushes a removal delta when farmland is destroyed, but only if the position
     * was actually showing an overlay — a pristine farmland break sends nothing.
     * Reads the pre-removal {@link SoilData} (the {@code onRemove} inject fires
     * before reversion clears investments).
     */
    public static void notifyFarmlandRemoved(ServerLevel level, BlockPos pos) {
        CultivationConfig config = CultivationConfig.get();
        if (!config.enableSoilFertility) {
            return;
        }
        SoilData data = SoilStores.peek(level, pos);
        if (data == null) {
            return; // untracked — client never showed anything here
        }
        byte flags = SoilOverlayFlags.computeFlags(data, config.tiredThreshold);
        if (SoilOverlayFlags.isDeviating(flags)) {
            sendDelta(level, pos, false, (byte) 0);
        }
    }

    private static void sendDelta(ServerLevel level, BlockPos pos, boolean present, byte flags) {
        ChunkPos chunkPos = new ChunkPos(pos);
        SoilBandDeltaS2CPayload payload =
                new SoilBandDeltaS2CPayload(chunkPos.toLong(), SoilStore.pack(pos), present, flags);
        for (ServerPlayer player : PlayerLookup.tracking(level, chunkPos)) {
            ServerPlayNetworking.send(player, payload);
        }
    }
}
