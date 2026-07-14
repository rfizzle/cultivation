package com.rfizzle.cultivation.soil;

import com.rfizzle.cultivation.config.CultivationConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The polyculture growth bonus ({@code design/SPEC.md} §2): a crop or stem
 * whose four cardinal same-Y neighbors include at least
 * {@code polycultureMinDifferentNeighbors} crops of a different identity grows
 * at {@code polycultureGrowthMultiplier}. Evaluated live at each growth roll —
 * four block reads, no stored state, no sync. Monoculture is never penalized;
 * the multiplier is always ≥ 1.0.
 *
 * <p>A qualifying row bordered by a <em>sniffer crop</em> — torchflower or
 * pitcher plant — earns the premium partner bonus: the fraction above 1.0 is
 * scaled by {@code snifferPolycultureBonusMultiplier} (default 2×, so the
 * standard +20% becomes +40%). The premium never reduces growth — the factor is
 * floored at 1.0 by its config clamp — so it stays positive-only like the base
 * bonus.
 */
public final class Polyculture {
    private Polyculture() {
    }

    /** A cardinal-neighbor tally: how many differ from the ticking crop, and whether any is a sniffer crop. */
    private record NeighborScan(int different, boolean sniffer) {
    }

    /**
     * The polyculture multiplier for the growth roll of {@code self} at
     * {@code pos}. Exactly 1.0 when the feature is disabled, the ticking block
     * has no crop identity, or the field around it is too uniform. A qualifying
     * field bordered by a sniffer crop earns the doubled premium.
     */
    public static float multiplierAt(ServerLevel level, BlockPos pos, BlockState self) {
        CultivationConfig config = CultivationConfig.get();
        if (!config.enablePolyculture) {
            return 1.0F;
        }
        ResourceLocation selfId = cropIdentity(self);
        if (selfId == null) {
            return 1.0F;
        }
        NeighborScan scan = scan(level, pos, selfId);
        return premiumMultiplier(scan.different(), scan.sniffer(),
                config.polycultureMinDifferentNeighbors, config.polycultureGrowthMultiplier,
                config.enableSnifferPolyculture, config.snifferPolycultureBonusMultiplier);
    }

    /**
     * Whether {@code self} at {@code pos} is currently receiving the sniffer
     * premium — a qualifying polyculture field with a sniffer-crop neighbor and
     * the premium enabled. The probe tooltip's honest "which magnitude" signal
     * (mc-probe-tooltips); off the growth hot path, so it rescans on demand.
     */
    public static boolean snifferPremiumActiveAt(ServerLevel level, BlockPos pos, BlockState self) {
        CultivationConfig config = CultivationConfig.get();
        if (!config.enablePolyculture || !config.enableSnifferPolyculture) {
            return false;
        }
        ResourceLocation selfId = cropIdentity(self);
        if (selfId == null) {
            return false;
        }
        NeighborScan scan = scan(level, pos, selfId);
        return scan.sniffer() && scan.different() >= config.polycultureMinDifferentNeighbors;
    }

    /** The four-direction cardinal tally around {@code pos} for a crop whose identity is {@code selfId}. */
    private static NeighborScan scan(ServerLevel level, BlockPos pos, ResourceLocation selfId) {
        int different = 0;
        boolean sniffer = false;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            ResourceLocation neighbor = cropIdentity(level.getBlockState(pos.relative(direction)));
            if (neighbor != null && !neighbor.equals(selfId)) {
                different++;
                if (isSnifferCrop(neighbor)) {
                    sniffer = true;
                }
            }
        }
        return new NeighborScan(different, sniffer);
    }

    /** Pure threshold math: the configured multiplier at or above the neighbor count, else 1.0. */
    public static float multiplier(int differentNeighbors, int minRequired, double configMultiplier) {
        return differentNeighbors >= minRequired ? (float) configMultiplier : 1.0F;
    }

    /**
     * The polyculture multiplier with the sniffer-partner premium (SPEC §2).
     * Below the threshold it is 1.0. At or above it, the bonus fraction
     * ({@code baseMultiplier - 1}) is returned as-is, or scaled by
     * {@code snifferBonus} when the premium is enabled and a different-crop
     * neighbor is a sniffer crop — the +20% becoming +40% at the 2× default.
     * Always ≥ 1.0: {@code snifferBonus} is clamped to ≥ 1.0, so the premium
     * only ever adds.
     */
    public static float premiumMultiplier(int differentNeighbors, boolean snifferNeighbor,
            int minRequired, double baseMultiplier, boolean premiumEnabled, double snifferBonus) {
        if (differentNeighbors < minRequired) {
            return 1.0F;
        }
        double bonus = baseMultiplier - 1.0;
        if (premiumEnabled && snifferNeighbor) {
            bonus *= snifferBonus;
        }
        return (float) (1.0 + bonus);
    }

    /**
     * Whether {@code id} is a sniffer crop — the premium polyculture partners,
     * torchflower and pitcher plant. Torchflower's two growth stages share the
     * {@code torchflower_crop} identity ({@link #cropIdentity} collapses the
     * mature flower onto it), so this single id covers both; the pitcher crop
     * keeps its own {@code pitcher_crop} id.
     */
    public static boolean isSnifferCrop(@Nullable ResourceLocation id) {
        return id != null
                && (id.equals(id(Blocks.TORCHFLOWER_CROP)) || id.equals(id(Blocks.PITCHER_CROP)));
    }

    /** How many of {@code neighbors} carry a crop identity different from {@code selfId}. */
    public static int countDifferent(ResourceLocation selfId, BlockState... neighbors) {
        int count = 0;
        for (BlockState neighbor : neighbors) {
            ResourceLocation id = cropIdentity(neighbor);
            if (id != null && !id.equals(selfId)) {
                count++;
            }
        }
        return count;
    }

    /**
     * The crop identity {@code state} contributes to neighbor comparison, or
     * null when it is not a crop. Identity survives maturity: an attached stem
     * keeps its base stem's id (melon and pumpkin stay two distinct crops) and
     * the mature torchflower — the one crop whose maturity changes its block
     * id — keeps {@code torchflower_crop}. Vanilla's attached stems hold their
     * base-stem link in a private field, so the two blocks are mapped by
     * identity; a modded {@link AttachedStemBlock} falls back to its own id.
     */
    @Nullable
    public static ResourceLocation cropIdentity(BlockState state) {
        Block block = state.getBlock();
        if (block == Blocks.ATTACHED_MELON_STEM) {
            return id(Blocks.MELON_STEM);
        }
        if (block == Blocks.ATTACHED_PUMPKIN_STEM) {
            return id(Blocks.PUMPKIN_STEM);
        }
        if (state.is(Blocks.TORCHFLOWER)) {
            return id(Blocks.TORCHFLOWER_CROP);
        }
        if (block instanceof CropBlock
                || block instanceof StemBlock
                || block instanceof AttachedStemBlock
                || block instanceof PitcherCropBlock) {
            return id(block);
        }
        return null;
    }

    private static ResourceLocation id(Block block) {
        return BuiltInRegistries.BLOCK.getKey(block);
    }
}
