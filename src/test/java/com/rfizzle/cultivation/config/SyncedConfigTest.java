// Tier: 1 (pure JUnit)
package com.rfizzle.cultivation.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The server-authoritative read seam: what a client-reachable rule resolves to
 * while connected, and that a disconnect drops the previous server's rules.
 *
 * <p>Only the synced-present paths are asserted here — the standalone fallback
 * routes through {@link CultivationConfig#get()}, which loads from
 * {@code FabricLoader}'s config dir and so is out of reach of a pure test.
 */
class SyncedConfigTest {
    @BeforeEach
    @AfterEach
    void resetSyncedState() {
        // Static holder: leave it clean so ordering never leaks into a sibling test.
        SyncedConfig.clear();
    }

    @Test
    void acceptedConfigIsWhatServerAuthoritativeReadsSee() {
        CultivationConfig fromServer = new CultivationConfig();
        SyncedConfig.accept(fromServer);

        assertSame(fromServer, SyncedConfig.serverConfig(), "the synced copy must be readable back");
        assertSame(fromServer, SyncedConfig.effective(),
                "a server-authoritative read must resolve to the synced copy, not the local file");
    }

    @Test
    void aLaterSyncReplacesTheEarlierOne() {
        CultivationConfig onJoin = new CultivationConfig();
        CultivationConfig afterReload = new CultivationConfig();
        SyncedConfig.accept(onJoin);
        SyncedConfig.accept(afterReload);

        assertSame(afterReload, SyncedConfig.effective(),
                "a post-reload re-broadcast must supersede the join-time copy");
    }

    @Test
    void acceptJsonPublishesAClampedConfig() {
        CultivationConfig fromServer = new CultivationConfig();
        fromServer.harvestDrain = 9999.0; // past the [0, 100] clamp range

        SyncedConfig.acceptJson(fromServer.toSyncJson());

        CultivationConfig synced = SyncedConfig.serverConfig();
        assertNotNull(synced, "a readable sync must be published");
        assertEquals(100.0, synced.harvestDrain,
                "the decoded config must be clamped before it is published — the bytes came off"
                        + " the network, and a server on a different build can send a value this"
                        + " client's bounds reject");
    }

    @Test
    void anUnreadableSyncKeepsThePreviousCopyRatherThanSeatingDefaults() {
        CultivationConfig fromServer = new CultivationConfig();
        fromServer.enablePolyculture = false;
        SyncedConfig.acceptJson(fromServer.toSyncJson());

        SyncedConfig.acceptJson("this is not json");

        CultivationConfig synced = SyncedConfig.serverConfig();
        assertNotNull(synced, "an unreadable sync must not clear a good previous copy");
        assertEquals(false, synced.enablePolyculture,
                "healing to defaults would flip a feature the server had disabled back on —"
                        + " precisely the direction server-authoritative config exists to prevent");
    }

    @Test
    void anUnreadableFirstSyncLeavesNothingSeated() {
        SyncedConfig.acceptJson("{{{ not json");

        assertNull(SyncedConfig.serverConfig(),
                "a failed first sync must leave the holder unset so effective() falls back to the"
                        + " local file, rather than publishing a config the server never sent");
    }

    @Test
    void clearDropsTheServerCopy() {
        SyncedConfig.accept(new CultivationConfig());
        SyncedConfig.clear();

        assertNull(SyncedConfig.serverConfig(),
                "disconnect must drop the server copy so its rules never bleed into the next world");
    }
}
