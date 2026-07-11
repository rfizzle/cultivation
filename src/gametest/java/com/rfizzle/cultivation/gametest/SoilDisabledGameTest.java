package com.rfizzle.cultivation.gametest;

import com.rfizzle.cultivation.attachment.SoilData;
import com.rfizzle.cultivation.config.CultivationConfig;
import com.rfizzle.cultivation.soil.SoilClockState;
import com.rfizzle.cultivation.soil.SoilGrowth;
import com.rfizzle.cultivation.soil.SoilRecovery;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import static com.rfizzle.cultivation.gametest.SoilFixtures.CROP;
import static com.rfizzle.cultivation.gametest.SoilFixtures.FARM;
import static com.rfizzle.cultivation.gametest.SoilFixtures.TEMPLATE;
import static com.rfizzle.cultivation.gametest.SoilFixtures.assertFertility;
import static com.rfizzle.cultivation.gametest.SoilFixtures.idOf;
import static com.rfizzle.cultivation.gametest.SoilFixtures.matureWheat;
import static com.rfizzle.cultivation.gametest.SoilFixtures.placeTrackedFarmland;

/**
 * {@code enableSoilFertility=false} freezes the whole system — drain, recovery,
 * growth multipliers, bone meal, and the soil clock — while retaining stored
 * data untouched. Runs in its own batch because it flips global config; the
 * yield clamp shares the exact guard the frozen-drain assertion covers.
 */
public class SoilDisabledGameTest implements FabricGameTest {
    private static final String BATCH = "cultivationFrozen";

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 200)
    public void disabledSoilSystemIsFrozen(GameTestHelper helper) {
        HarvestRecorder.ensureRegistered();
        CultivationConfig config = CultivationConfig.get();
        boolean saved = config.enableSoilFertility;
        config.enableSoilFertility = false;
        try {
            ServerLevel level = helper.getLevel();
            placeTrackedFarmland(helper, FARM, 50.0F, Blocks.WHEAT);
            helper.setBlock(CROP, matureWheat());
            var cropAbs = helper.absolutePos(CROP);

            // Drain frozen, data retained untouched — but the harvest seam still fires its callback.
            level.destroyBlock(cropAbs, true);
            assertFertility(helper, FARM, 50.0F, "drain must be frozen while disabled");
            SoilData data = SoilFixtures.data(helper, FARM);
            helper.assertTrue(data != null && data.lastCrop().map(idOf(Blocks.WHEAT)::equals).orElse(false),
                    "stored data must be retained untouched");
            helper.assertTrue(HarvestRecorder.RECORDS.stream().anyMatch(r -> r.pos().equals(cropAbs)),
                    "the harvest callback is API surface and fires regardless of the soil toggle");

            // Growth multiplier pinned to 1.0 even on exhausted soil.
            placeTrackedFarmland(helper, FARM, 0.0F, Blocks.WHEAT);
            helper.setBlock(CROP, Blocks.WHEAT.defaultBlockState());
            helper.assertTrue(SoilGrowth.multiplierAt(level, cropAbs) == 1.0F,
                    "all growth multipliers must be 1.0 while disabled");
            helper.setBlock(CROP, Blocks.AIR);

            // Live recovery frozen.
            SoilRecovery.onFarmlandRandomTick(level, helper.absolutePos(FARM));
            assertFertility(helper, FARM, 0.0F, "recovery must be frozen while disabled");

            // Bone meal amendment frozen: no effect, item kept.
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            ItemStack boneMeal = new ItemStack(Items.BONE_MEAL, 5);
            player.setItemInHand(InteractionHand.MAIN_HAND, boneMeal);
            var farmAbs = helper.absolutePos(FARM);
            InteractionResult result = UseBlockCallback.EVENT.invoker().interact(player, level,
                    InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(farmAbs), Direction.UP, farmAbs, false));
            helper.assertTrue(result == InteractionResult.PASS && boneMeal.getCount() == 5,
                    "bone meal amendment must be inert while disabled");
            player.discard();

            // The fallow clock does not accrue while disabled.
            long frozenAt = SoilClockState.get(level).time();
            helper.runAfterDelay(5, () -> {
                try {
                    helper.assertTrue(SoilClockState.get(helper.getLevel()).time() == frozenAt,
                            "the soil clock must not advance while disabled");
                    helper.succeed();
                } finally {
                    CultivationConfig.get().enableSoilFertility = saved;
                }
            });
        } catch (Throwable t) {
            config.enableSoilFertility = saved;
            throw t;
        }
    }

    /** Safety net: a timeout inside the delayed continuation must not leak the toggle. */
    @AfterBatch(batch = BATCH)
    public void restoreSoilToggle(ServerLevel level) {
        CultivationConfig.get().enableSoilFertility = true;
    }
}
