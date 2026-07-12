package com.rfizzle.cultivation.api;

import com.rfizzle.cultivation.attachment.DietStore;
import com.rfizzle.cultivation.attachment.SoilData;
import com.rfizzle.cultivation.attachment.SoilStores;
import com.rfizzle.cultivation.config.CultivationConfig;
import com.rfizzle.cultivation.soil.SoilMath;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
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

    /**
     * The dietary-fatigue multiplier the player's next eat of {@code stack} would
     * receive, in {@code [fatigueFloor, 1.0]} (the floor is configurable, so the
     * lower bound follows the server's {@code fatigueFloor}). Returns {@code 1.0}
     * when dietary fatigue is disabled. Cake stacks key to {@code minecraft:cake}.
     */
    public static float getFoodEffectiveness(ServerPlayer player, ItemStack stack) {
        CultivationConfig config = CultivationConfig.get();
        if (!config.enableDietaryFatigue) {
            return 1.0F;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return (float) DietStore.get(player).effectiveness(id, config.fatiguePerRepeat, config.fatigueFloor);
    }
}
