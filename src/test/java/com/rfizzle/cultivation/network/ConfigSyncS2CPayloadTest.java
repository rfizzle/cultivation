package com.rfizzle.cultivation.network;

import com.rfizzle.cultivation.config.CultivationConfig;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigSyncS2CPayloadTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    }

    @Test
    void roundTripsServerConfig() {
        CultivationConfig config = new CultivationConfig();
        config.harvestDrain = 7.5;
        config.enablePolyculture = false;
        config.fertilizerDoseHarvests = 42;

        RegistryFriendlyByteBuf buf = buffer();
        ConfigSyncS2CPayload.CODEC.encode(buf, new ConfigSyncS2CPayload(config));
        ConfigSyncS2CPayload decoded = ConfigSyncS2CPayload.CODEC.decode(buf);

        assertEquals(7.5, decoded.config().harvestDrain);
        assertFalse(decoded.config().enablePolyculture);
        assertEquals(42, decoded.config().fertilizerDoseHarvests);
    }

    @Test
    void decodeClampsOutOfRangeRule() {
        CultivationConfig config = new CultivationConfig();
        config.harvestDrain = 9999.0; // past the [0, 100] clamp range

        RegistryFriendlyByteBuf buf = buffer();
        ConfigSyncS2CPayload.CODEC.encode(buf, new ConfigSyncS2CPayload(config));
        ConfigSyncS2CPayload decoded = ConfigSyncS2CPayload.CODEC.decode(buf);

        assertEquals(100.0, decoded.config().harvestDrain);
    }

    @Test
    void emptyPayloadIsRejected() {
        RegistryFriendlyByteBuf buf = buffer();
        buf.writeUtf("");
        assertThrows(DecoderException.class, () -> ConfigSyncS2CPayload.CODEC.decode(buf));
    }
}
