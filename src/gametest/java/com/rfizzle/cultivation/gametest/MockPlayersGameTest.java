package com.rfizzle.cultivation.gametest;

import com.rfizzle.cultivation.gametest.util.MockPlayers;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

/**
 * Guards {@link MockPlayers}' faithfulness to the vanilla connected-player
 * construction — a later "simplification" to a bare {@code new ServerPlayer}
 * must fail here instead of silently breaking connection-dependent tests — and
 * the teardown contract {@code MockPlayerDiscardTest} enforces at Tier 1.
 *
 * <p>Every method here owns one batch. These are the only tests in the suite
 * that assert on the player list itself, so running them alongside a sibling
 * building or retiring its own mock would make them flaky against state neither
 * test controls.
 */
public class MockPlayersGameTest implements FabricGameTest {
    private static final String BATCH = "cultivationMockPlayers";

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH)
    public void connectedReplicaIsFaithful(GameTestHelper helper) {
        MockPlayers.Connected connected = MockPlayers.connectedServerPlayerInLevel(helper);
        ServerPlayer player = connected.player();
        try {
            helper.assertTrue(player.connection != null, "mock player has no ServerGamePacketListenerImpl");
            helper.assertTrue(connected.channel() != null,
                    "the connected replica must hand back the channel its outbound packets land in");
            helper.assertTrue(
                    helper.getLevel().getServer().getPlayerList().getPlayers().contains(player),
                    "mock player is not registered in the player list");
            helper.assertTrue(player.level() == helper.getLevel(), "mock player is not in the test level");
            helper.assertTrue(player.isCreative(), "mock player must report creative like the vanilla helper");
            helper.assertTrue(!player.isSpectator(), "mock player must not be a spectator");
            helper.assertTrue(MockPlayers.MOCK_NAME.equals(player.getGameProfile().getName()),
                    "the mock profile name must be mod-scoped so a sibling mod's leak sweep cannot"
                            + " retire this player, got " + player.getGameProfile().getName());
            helper.succeed();
        } finally {
            MockPlayers.retire(player);
        }
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH)
    public void spectatorReplicaReportsSpectator(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.spectatorServerPlayerInLevel(helper).player();
        try {
            helper.assertTrue(player.isSpectator(), "the spectator variant must report isSpectator()");
            helper.assertTrue(
                    helper.getLevel().getServer().getPlayerList().getPlayers().contains(player),
                    "the spectator variant is still a fully connected, player-list-registered replica");
            helper.succeed();
        } finally {
            MockPlayers.retire(player);
        }
    }

    /**
     * {@code retire} is what every other suite's {@code finally} now calls, so its
     * two halves are pinned here: it must actually clear the player-list entry a
     * bare {@code discard()} leaves behind, and a second call must be a no-op.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = BATCH)
    public void retireClearsThePlayerListEntryAndIsIdempotent(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        // This is the one test that retires mid-body, because retiring is what it asserts on.
        // The finally still retires: idempotence is the property under test, so the trailing
        // call is both the cleanup an assertion-that-throws needs and a third exercise of it.
        try {
            helper.assertTrue(
                    helper.getLevel().getServer().getPlayerList().getPlayers().contains(player),
                    "precondition: the replica starts in the player list");

            MockPlayers.retire(player);

            helper.assertTrue(player.isRemoved(), "retire must discard the entity");
            helper.assertTrue(
                    !helper.getLevel().getServer().getPlayerList().getPlayers().contains(player),
                    "retire must clear the player-list entry — a bare discard() leaves it, and with"
                            + " it every advancement listener that entry keeps registered");

            // The second call must return without re-entering PlayerList#remove, which would
            // rewrite the player .dat, the stats JSON and the advancements JSON, and inflate
            // LEAVE_GAME.
            MockPlayers.retire(player);
            helper.assertTrue(
                    !helper.getLevel().getServer().getPlayerList().getPlayers().contains(player),
                    "a second retire must be a no-op");
            helper.succeed();
        } finally {
            MockPlayers.retire(player);
        }
    }
}
