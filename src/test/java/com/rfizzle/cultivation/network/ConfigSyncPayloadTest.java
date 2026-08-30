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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The wire contract for the config sync: the codec moves a bounded string and
 * nothing else, and the interpretation of that string is a separate, fallible
 * step the handler owns.
 */
class ConfigSyncPayloadTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    }

    private static ConfigSyncPayload roundTrip(ConfigSyncPayload payload) {
        RegistryFriendlyByteBuf buf = buffer();
        ConfigSyncPayload.CODEC.encode(buf, payload);
        return ConfigSyncPayload.CODEC.decode(buf);
    }

    @Test
    void roundTripsServerConfig() {
        CultivationConfig config = new CultivationConfig();
        config.harvestDrain = 7.5;
        config.enablePolyculture = false;
        config.fertilizerDoseHarvests = 42;

        CultivationConfig decoded = CultivationConfig.fromSyncJson(roundTrip(ConfigSyncPayload.of(config)).json());

        assertNotNull(decoded);
        assertEquals(7.5, decoded.harvestDrain);
        assertFalse(decoded.enablePolyculture);
        assertEquals(42, decoded.fertilizerDoseHarvests);
    }

    @Test
    void parsingClampsOutOfRangeRule() {
        CultivationConfig config = new CultivationConfig();
        config.harvestDrain = 9999.0; // past the [0, 100] clamp range

        CultivationConfig decoded = CultivationConfig.fromSyncJson(roundTrip(ConfigSyncPayload.of(config)).json());

        assertNotNull(decoded);
        assertEquals(100.0, decoded.harvestDrain);
    }

    @Test
    void decodeReadsTheBodyWithoutInterpretingIt() {
        // The codec's whole job. A blob that is not a config decodes fine and fails later,
        // in the handler, where a failure can be logged instead of dropping the connection.
        ConfigSyncPayload decoded = roundTrip(new ConfigSyncPayload("this is not json"));
        assertEquals("this is not json", decoded.json());
    }

    @Test
    void anUnreadableBodyParsesToNullRatherThanDefaults() {
        // Defaults would read as a successful sync while seating a config the server never
        // sent — and every default here is the permissive one, so the client would enable
        // features the server had disabled.
        assertNull(CultivationConfig.fromSyncJson("this is not json"));
        assertNull(CultivationConfig.fromSyncJson("[1, 2, 3]"));
    }

    @Test
    void anOverlongBodyIsRejectedAtTheWire() {
        RegistryFriendlyByteBuf buf = buffer();
        buf.writeUtf("x".repeat(ConfigSyncPayload.MAX_JSON_CHARS + 1), ConfigSyncPayload.MAX_JSON_CHARS + 1);
        assertThrows(DecoderException.class, () -> ConfigSyncPayload.CODEC.decode(buf));
    }

    @Test
    void theWireBlobIsCompactNotPrettyPrinted() {
        // Pretty-printing is roughly 40% of a payload sent to every player on join and after
        // every reload, and nothing on the other end reads the indentation.
        String json = new CultivationConfig().toSyncJson();
        assertFalse(json.contains("\n"), "the sync blob must not be pretty-printed: " + json);
        assertTrue(json.length() < ConfigSyncPayload.MAX_JSON_CHARS / 4,
                "the config should sit far inside the wire cap, got " + json.length() + " chars");
    }
}
