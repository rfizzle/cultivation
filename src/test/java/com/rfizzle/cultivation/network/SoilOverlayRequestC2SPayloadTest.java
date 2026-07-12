package com.rfizzle.cultivation.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SoilOverlayRequestC2SPayloadTest {
    @Test
    void roundTripsChunkCoordinates() {
        SoilOverlayRequestC2SPayload payload = new SoilOverlayRequestC2SPayload(-7, 128);
        ByteBuf buf = Unpooled.buffer();
        SoilOverlayRequestC2SPayload.CODEC.encode(buf, payload);
        SoilOverlayRequestC2SPayload decoded = SoilOverlayRequestC2SPayload.CODEC.decode(buf);

        assertEquals(-7, decoded.chunkX());
        assertEquals(128, decoded.chunkZ());
    }
}
