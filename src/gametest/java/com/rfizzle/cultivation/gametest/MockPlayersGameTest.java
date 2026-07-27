package com.rfizzle.cultivation.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

/**
 * Guards {@link MockPlayers}' faithfulness to the vanilla connected-player
 * construction — a later "simplification" to a bare {@code new ServerPlayer}
 * must fail here instead of silently breaking connection-dependent tests.
 */
public class MockPlayersGameTest implements FabricGameTest {

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void connectedReplicaIsFaithful(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        try {
            helper.assertTrue(player.connection != null, "mock player has no ServerGamePacketListenerImpl");
            helper.assertTrue(
                    helper.getLevel().getServer().getPlayerList().getPlayers().contains(player),
                    "mock player is not registered in the player list");
            helper.assertTrue(player.level() == helper.getLevel(), "mock player is not in the test level");
            helper.assertTrue(player.isCreative(), "mock player must report creative like the vanilla helper");
            helper.assertTrue(!player.isSpectator(), "mock player must not be a spectator");
            helper.succeed();
        } finally {
            player.discard();
        }
    }
}
