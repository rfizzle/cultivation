package com.rfizzle.cultivation.api;

import com.rfizzle.cultivation.attachment.SoilData;
import com.rfizzle.cultivation.attachment.SoilStores;
import com.rfizzle.cultivation.soil.SoilMath;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

import java.util.Optional;

/**
 * Cultivation's read-only static facade (Concord API Standard). All reads are
 * server-authoritative and never mutate soil state — outside mods observe
 * fertility and react; they don't write it. The one sanctioned mutation point
 * is {@link CultivationHarvestCallback}'s drops list.
 */
@Stable
public final class CultivationAPI {
    private CultivationAPI() {
    }

    /**
     * The block's fertility, 0–100. Untracked farmland is pristine {@code 100};
     * a block that is not farmland returns {@code -1}.
     */
    public static float getFertility(ServerLevel level, BlockPos pos) {
        if (!level.getBlockState(pos).is(Blocks.FARMLAND)) {
            return -1.0F;
        }
        return SoilStores.fertilityAt(level, pos);
    }

    /** The block's full soil snapshot; empty if the block is not farmland. */
    public static Optional<SoilInfo> getSoilInfo(ServerLevel level, BlockPos pos) {
        if (!level.getBlockState(pos).is(Blocks.FARMLAND)) {
            return Optional.empty();
        }
        SoilData data = SoilStores.peek(level, pos);
        if (data == null) {
            return Optional.of(new SoilInfo(SoilMath.MAX_FERTILITY, 0, 0, Optional.empty()));
        }
        return Optional.of(new SoilInfo(
                data.fertility(), data.enrichedChance(), data.fertilizerRemaining(), data.lastCrop()));
    }
}
