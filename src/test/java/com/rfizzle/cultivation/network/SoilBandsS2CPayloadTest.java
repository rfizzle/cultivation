package com.rfizzle.cultivation.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.codec.ByteBufCodecs;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoilBandsS2CPayloadTest {
    @Test
    void roundTripsEntries() {
        List<SoilBandsS2CPayload.Entry> entries = List.of(
                new SoilBandsS2CPayload.Entry(1234, (byte) 0b0010),
                new SoilBandsS2CPayload.Entry(5678, (byte) 0b1111));
        SoilBandsS2CPayload payload = new SoilBandsS2CPayload(42L, entries);

        ByteBuf buf = Unpooled.buffer();
        SoilBandsS2CPayload.CODEC.encode(buf, payload);
        SoilBandsS2CPayload decoded = SoilBandsS2CPayload.CODEC.decode(buf);

        assertEquals(42L, decoded.chunkPos());
        assertEquals(entries, decoded.entries());
    }

    @Test
    void emptyEntriesRoundTrip() {
        ByteBuf buf = Unpooled.buffer();
        SoilBandsS2CPayload.CODEC.encode(buf, new SoilBandsS2CPayload(7L, List.of()));
        SoilBandsS2CPayload decoded = SoilBandsS2CPayload.CODEC.decode(buf);
        assertEquals(7L, decoded.chunkPos());
        assertTrue(decoded.entries().isEmpty());
    }

    @Test
    void oversizeEntryListIsRejected() {
        ByteBuf buf = Unpooled.buffer();
        buf.writeLong(0L);
        ByteBufCodecs.VAR_INT.encode(buf, 8193);
        assertThrows(DecoderException.class, () -> SoilBandsS2CPayload.CODEC.decode(buf));
    }
}
