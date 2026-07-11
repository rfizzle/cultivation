package com.rfizzle.cultivation.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;

import java.util.UUID;

/**
 * Gametest helper for a fully connected {@link ServerPlayer}.
 * {@code GameTestHelper#makeMockServerPlayerInLevel()} is deprecated for
 * removal in 1.21.1 with no replacement, so this reproduces its construction
 * faithfully with public, non-deprecated APIs: a real {@link Connection}
 * backed by an {@link EmbeddedChannel} (absorbs sent packets), registered in
 * the player list via {@code placeNewPlayer}, forced non-spectator and
 * creative exactly like the vanilla method. {@code MockPlayersGameTest} guards
 * this faithfulness so a later simplification fails loudly.
 */
public final class MockPlayers {
    private MockPlayers() {
    }

    public static ServerPlayer serverPlayerInLevel(GameTestHelper helper) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), "test-mock-player");
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);

        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        ServerPlayer player = new ServerPlayer(server, level, cookie.gameProfile(), cookie.clientInformation()) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return true;
            }
        };

        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        return player;
    }
}
