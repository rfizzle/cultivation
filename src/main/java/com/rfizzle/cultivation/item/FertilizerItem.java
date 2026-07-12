package com.rfizzle.cultivation.item;

import com.rfizzle.cultivation.criteria.CultivationCriteria;
import com.rfizzle.cultivation.soil.Fertilizer;
import com.rfizzle.cultivation.soil.SupportedCrops;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

/**
 * The composter-made Fertilizer ({@code design/SPEC.md} §6). Used on farmland —
 * or on the crop growing on it, resolving to the farmland beneath — it fills the
 * block's dose counter and is consumed; the harvest choke point spends the dose
 * for a guaranteed bonus product. It is not a growth accelerant: it never
 * advances crop age, and a use on an already-full dose or a non-farmland block
 * fails without consuming the item.
 */
public class FertilizerItem extends Item {
    public FertilizerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos soilPos = resolveFarmland(level, context.getClickedPos());
        if (soilPos == null) {
            return InteractionResult.PASS; // not usable on non-farmland
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.sidedSuccess(true); // client predicts the swing
        }
        if (!Fertilizer.applyDose(serverLevel, soilPos, context.getPlayer())) {
            return InteractionResult.PASS; // disabled or already full: no consume
        }
        Player player = context.getPlayer();
        if (player == null || !player.getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
        // Player-driven only: the villager stewardship dose reaches Fertilizer
        // #applyDose with a null player and grants nothing (§10).
        if (player instanceof ServerPlayer serverPlayer) {
            CultivationCriteria.LONG_TERM_INVESTMENT.trigger(serverPlayer);
        }
        return InteractionResult.sidedSuccess(false);
    }

    /**
     * The farmland this use targets: the clicked block when it is farmland, else
     * the farmland beneath a crop the click landed on. A use on any other block —
     * including a non-crop block that merely happens to rest on farmland — is not
     * a valid target, so {@code null}.
     */
    @Nullable
    private static BlockPos resolveFarmland(Level level, BlockPos clicked) {
        if (level.getBlockState(clicked).is(Blocks.FARMLAND)) {
            return clicked;
        }
        BlockPos below = clicked.below();
        if (SupportedCrops.isOccupying(level.getBlockState(clicked))
                && level.getBlockState(below).is(Blocks.FARMLAND)) {
            return below;
        }
        return null;
    }
}
