package com.rfizzle.cultivation.network;

import com.rfizzle.cultivation.Cultivation;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The client's request for a chunk's soil overlay set ({@code design/SPEC.md}
 * §1), sent once when the chunk loads client-side. The server validates the
 * chunk is loaded and near the player, rate-limits, and answers with a {@link
 * SoilBandsS2CPayload} of only the visually deviating positions.
 */
public record SoilOverlayRequestC2SPayload(int chunkX, int chunkZ) implements CustomPacketPayload {
    public static final Type<SoilOverlayRequestC2SPayload> TYPE =
            new Type<>(Cultivation.id("soil_overlay_request"));

    public static final StreamCodec<ByteBuf, SoilOverlayRequestC2SPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SoilOverlayRequestC2SPayload::chunkX,
            ByteBufCodecs.VAR_INT, SoilOverlayRequestC2SPayload::chunkZ,
            SoilOverlayRequestC2SPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
