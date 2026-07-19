package com.rfizzle.cultivation.event;

import com.rfizzle.cultivation.attachment.SoilData;
import com.rfizzle.cultivation.attachment.SoilStores;
import com.rfizzle.cultivation.config.CultivationConfig;
import com.rfizzle.cultivation.soil.LevelEvents;
import com.rfizzle.cultivation.soil.SoilMath;
import com.rfizzle.cultivation.soil.SupportedCrops;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Player block-use seams for {@code design/SPEC.md} §1, registered before
 * vanilla item behavior:
 *
 * <ul>
 * <li><b>Bone meal amendment</b> — bone meal on fallow farmland restores
 * {@code boneMealFertilityRestore}, consumes the item, and plays the vanilla
 * bone-meal event. At fertility 100 the use fails without consuming (vanilla
 * no-ops on farmland, so PASS is exactly that). Bone meal aimed at a crop block
 * never reaches this branch and stays untouched vanilla growth behavior.</li>
 * <li><b>Till settle</b> — a hoe used on ground that tills to farmland settles
 * the position's accrued lazy recovery first, so re-tilling resumes from an
 * honestly settled value. Runs regardless of {@code enableSoilFertility}: it
 * only applies recovery owed from spans the soil clock actually advanced over
 * (enabled time), and skipping it would silently drop that owed recovery.</li>
 * </ul>
 */
public final class SoilInteractionHandler implements UseBlockCallback {
    private SoilInteractionHandler() {
    }

    public static void register() {
        UseBlockCallback.EVENT.register(new SoilInteractionHandler());
    }

    @Override
    public InteractionResult interact(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
        if (!(level instanceof ServerLevel serverLevel) || player.isSpectator()) {
            return InteractionResult.PASS;
        }
        ItemStack stack = player.getItemInHand(hand);
        BlockPos pos = hit.getBlockPos();
        BlockState state = serverLevel.getBlockState(pos);

        if (stack.getItem() instanceof HoeItem && tillsToFarmland(state)) {
            SoilStores.settle(serverLevel, pos);
            return InteractionResult.PASS; // vanilla performs the actual till
        }

        if (stack.is(Items.BONE_MEAL) && state.is(Blocks.FARMLAND)) {
            return boneMealAmendment(player, serverLevel, pos, stack);
        }
        return InteractionResult.PASS;
    }

    private static InteractionResult boneMealAmendment(
            Player player, ServerLevel level, BlockPos pos, ItemStack stack) {
        CultivationConfig config = CultivationConfig.get();
        if (!config.enableSoilFertility) {
            return InteractionResult.PASS;
        }
        if (SupportedCrops.isOccupying(level.getBlockState(pos.above()))) {
            return InteractionResult.PASS; // not fallow — the block is worked ground
        }
        SoilData data = SoilStores.peek(level, pos);
        if (data == null || data.fertility() >= SoilMath.MAX_FERTILITY) {
            return InteractionResult.PASS; // already pristine: no effect, item not consumed
        }
        SoilStores.update(level, pos, true,
                current -> current.withFertility(current.fertility() + (float) config.boneMealFertilityRestore));
        level.levelEvent(LevelEvents.BONE_MEAL, pos, 15);
        player.gameEvent(GameEvent.ITEM_INTERACT_FINISH);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResult.SUCCESS;
    }

    /** The three ground blocks a hoe converts to farmland (SPEC §5's till surface). */
    private static boolean tillsToFarmland(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.DIRT_PATH);
    }
}
