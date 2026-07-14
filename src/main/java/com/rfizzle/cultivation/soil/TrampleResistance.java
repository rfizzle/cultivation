package com.rfizzle.cultivation.soil;

import com.rfizzle.cultivation.attachment.SoilData;
import com.rfizzle.cultivation.attachment.SoilStores;
import com.rfizzle.cultivation.config.CultivationConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Enriched farmland resists a player's trampling ({@code design/SPEC.md} §5):
 * a diamond- or netherite-tilled block ({@code enrichedChance > 0}) is not
 * reverted to dirt by a player's stomp, so a long-invested plot survives its
 * own gardener's jump. Mobs are never covered — crop-trampling raiders and
 * world danger stay Tribulation's ground ({@code design/VISION.md}) — and plain
 * farmland stays exactly as fragile as vanilla made it. Wired in by the
 * {@code FarmBlock#fallOn} mixin, which skips the vanilla {@code turnToDirt}
 * call when this resists.
 */
public final class TrampleResistance {
    private TrampleResistance() {
    }

    /**
     * Pure decision: an enabled toggle resists only a player's trample of an
     * enriched block. A non-player trampler, an un-enriched block, or a disabled
     * toggle all let the vanilla revert proceed.
     */
    public static boolean resistsTrample(boolean enabled, boolean trampledByPlayer, int enrichedChance) {
        return enabled && trampledByPlayer && enrichedChance > 0;
    }

    /**
     * Thin shell for the {@code FarmBlock#fallOn} seam: reads the toggle and the
     * block's stored enrichment and decides whether to suppress the vanilla
     * revert-to-dirt. Client-side calls and untracked (pristine) positions never
     * resist.
     */
    public static boolean shouldResist(@Nullable Entity trampler, Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        SoilData data = SoilStores.peek(serverLevel, pos);
        int enrichedChance = data == null ? 0 : data.enrichedChance();
        return resistsTrample(
                CultivationConfig.get().enrichedSoilResistsTrampling,
                trampler instanceof Player,
                enrichedChance);
    }
}
