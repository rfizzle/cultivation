package com.rfizzle.cultivation.network;

import com.rfizzle.cultivation.Cultivation;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * A single-position change push ({@code design/SPEC.md} §1): sent to the players
 * tracking a chunk when one position's overlay state changes — a band boundary
 * crossed, a Fertilizer dose started or ran out, enrichment set or cleared, or
 * the farmland removed.
 *
 * <p>{@code present} is false when the position no longer shows anything (it
 * became Rich/Fair uninvested, or its farmland is gone); the client drops it from
 * its cache. When true, {@code flags} is the new overlay
 * {@link com.rfizzle.cultivation.soil.SoilOverlayFlags flag byte}.
 */
public record SoilBandDeltaS2CPayload(long chunkPos, int packedPos, boolean present, byte flags)
        implements CustomPacketPayload {
    public static final Type<SoilBandDeltaS2CPayload> TYPE = new Type<>(Cultivation.id("soil_band_delta"));

    public static final StreamCodec<ByteBuf, SoilBandDeltaS2CPayload> CODEC =
            StreamCodec.of(SoilBandDeltaS2CPayload::encode, SoilBandDeltaS2CPayload::decode);

    private static void encode(ByteBuf buf, SoilBandDeltaS2CPayload payload) {
        buf.writeLong(payload.chunkPos);
        ByteBufCodecs.VAR_INT.encode(buf, payload.packedPos);
        buf.writeBoolean(payload.present);
        buf.writeByte(payload.flags);
    }

    private static SoilBandDeltaS2CPayload decode(ByteBuf buf) {
        long chunkPos = buf.readLong();
        int packedPos = ByteBufCodecs.VAR_INT.decode(buf);
        boolean present = buf.readBoolean();
        byte flags = buf.readByte();
        return new SoilBandDeltaS2CPayload(chunkPos, packedPos, present, flags);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
