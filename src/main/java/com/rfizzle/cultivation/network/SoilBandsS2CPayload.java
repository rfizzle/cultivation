package com.rfizzle.cultivation.network;

import com.rfizzle.cultivation.Cultivation;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * The server's answer to a {@link SoilOverlayRequestC2SPayload}: every visually
 * deviating position in one chunk ({@code design/SPEC.md} §1). Each entry is a
 * packed in-chunk position ({@link com.rfizzle.cultivation.attachment.SoilStore#pack})
 * plus its overlay {@link com.rfizzle.cultivation.soil.SoilOverlayFlags flag byte}.
 * A chunk with nothing to show sends an empty list, which clears any stale client
 * cache for that chunk.
 */
public record SoilBandsS2CPayload(long chunkPos, List<Entry> entries) implements CustomPacketPayload {
    public static final Type<SoilBandsS2CPayload> TYPE = new Type<>(Cultivation.id("soil_bands"));

    // A 16x16 column across the full build height has far fewer than this many
    // farmland positions; reject any list past a safe ceiling before allocating.
    private static final int MAX_ENTRIES = 8192;

    public static final StreamCodec<ByteBuf, SoilBandsS2CPayload> CODEC =
            StreamCodec.of(SoilBandsS2CPayload::encode, SoilBandsS2CPayload::decode);

    /** One deviating position: its packed in-chunk key and overlay flags. */
    public record Entry(int packedPos, byte flags) {
    }

    private static void encode(ByteBuf buf, SoilBandsS2CPayload payload) {
        buf.writeLong(payload.chunkPos);
        ByteBufCodecs.VAR_INT.encode(buf, payload.entries.size());
        for (Entry entry : payload.entries) {
            ByteBufCodecs.VAR_INT.encode(buf, entry.packedPos());
            buf.writeByte(entry.flags());
        }
    }

    private static SoilBandsS2CPayload decode(ByteBuf buf) {
        long chunkPos = buf.readLong();
        int size = ByteBufCodecs.VAR_INT.decode(buf);
        if (size < 0 || size > MAX_ENTRIES) {
            throw new DecoderException("Soil bands entry list too large: " + size);
        }
        List<Entry> entries = new ArrayList<>(Math.min(size, 256));
        for (int i = 0; i < size; i++) {
            int packedPos = ByteBufCodecs.VAR_INT.decode(buf);
            byte flags = buf.readByte();
            entries.add(new Entry(packedPos, flags));
        }
        return new SoilBandsS2CPayload(chunkPos, entries);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
