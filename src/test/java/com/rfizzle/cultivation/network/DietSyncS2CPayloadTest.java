package com.rfizzle.cultivation.network;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DietSyncS2CPayloadTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    }

    @Test
    void roundTripsStacksAndConfig() {
        Map<ResourceLocation, Integer> stacks = new LinkedHashMap<>();
        stacks.put(ResourceLocation.withDefaultNamespace("carrot"), 3);
        stacks.put(ResourceLocation.withDefaultNamespace("cake"), 5);
        DietSyncS2CPayload payload = new DietSyncS2CPayload(stacks, 0.1F, 0.5F);

        RegistryFriendlyByteBuf buf = buffer();
        DietSyncS2CPayload.CODEC.encode(buf, payload);
        DietSyncS2CPayload decoded = DietSyncS2CPayload.CODEC.decode(buf);

        assertEquals(stacks, decoded.stacks());
        assertEquals(0.1F, decoded.fatiguePerRepeat());
        assertEquals(0.5F, decoded.fatigueFloor());
    }

    @Test
    void emptyStacksRoundTrip() {
        RegistryFriendlyByteBuf buf = buffer();
        DietSyncS2CPayload.CODEC.encode(buf, new DietSyncS2CPayload(Map.of(), 0.1F, 0.5F));
        DietSyncS2CPayload decoded = DietSyncS2CPayload.CODEC.decode(buf);
        assertTrue(decoded.stacks().isEmpty());
    }

    @Test
    void oversizeCollectionIsRejected() {
        RegistryFriendlyByteBuf buf = buffer();
        // Hand-write a stack count past the ceiling; decode must reject it, not allocate for it.
        ByteBufCodecs.VAR_INT.encode(buf, 4097);
        assertThrows(DecoderException.class, () -> DietSyncS2CPayload.CODEC.decode(buf));
    }
}
