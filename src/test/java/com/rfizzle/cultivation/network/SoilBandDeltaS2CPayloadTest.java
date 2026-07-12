package com.rfizzle.cultivation.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoilBandDeltaS2CPayloadTest {
    @Test
    void roundTripsPresentDelta() {
        SoilBandDeltaS2CPayload payload = new SoilBandDeltaS2CPayload(99L, 4321, true, (byte) 0b0110);
        ByteBuf buf = Unpooled.buffer();
        SoilBandDeltaS2CPayload.CODEC.encode(buf, payload);
        SoilBandDeltaS2CPayload decoded = SoilBandDeltaS2CPayload.CODEC.decode(buf);

        assertEquals(99L, decoded.chunkPos());
        assertEquals(4321, decoded.packedPos());
        assertTrue(decoded.present());
        assertEquals((byte) 0b0110, decoded.flags());
    }

    @Test
    void roundTripsRemovalDelta() {
        SoilBandDeltaS2CPayload payload = new SoilBandDeltaS2CPayload(-3L, 17, false, (byte) 0);
        ByteBuf buf = Unpooled.buffer();
        SoilBandDeltaS2CPayload.CODEC.encode(buf, payload);
        SoilBandDeltaS2CPayload decoded = SoilBandDeltaS2CPayload.CODEC.decode(buf);

        assertEquals(-3L, decoded.chunkPos());
        assertEquals(17, decoded.packedPos());
        assertFalse(decoded.present());
    }
}
