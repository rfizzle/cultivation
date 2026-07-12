package com.rfizzle.cultivation.gametest;

import com.rfizzle.cultivation.config.CultivationConfig;
import com.rfizzle.cultivation.effect.CultivationEffects;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The meal-buff grants driven through the real world (SPEC §4): the {@code
 * Player#eat} bowl-food path and the {@code CakeBlock#eat} slice path grant the
 * right effects, replace the whole trio one-meal-at-a-time, and let Sated slow
 * real exhaustion. The selection/scaling math itself is covered at Tier 1 in
 * {@code MealBuffsTest}; these prove the seams wire it to real eating.
 */
public class MealBuffGameTest implements FabricGameTest {

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void rabbitStewGrantsNimbleAtLevelOne(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        eat(helper, player, Items.RABBIT_STEW);

        MobEffectInstance nimble = player.getEffect(CultivationEffects.NIMBLE);
        helper.assertTrue(nimble != null, "rabbit stew grants Nimble");
        helper.assertTrue(nimble.getAmplifier() == 0, "Nimble is level I");
        helper.assertTrue(nimble.getDuration() == CultivationConfig.get().mealBuffDurationTicks,
                "Nimble lasts mealBuffDurationTicks, got " + nimble.getDuration());
        helper.assertTrue(player.getEffect(CultivationEffects.DILIGENT) == null, "only Nimble is granted");
        helper.assertTrue(player.getEffect(CultivationEffects.SATED) == null, "only Nimble is granted");
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void beetrootSoupGrantsDiligentAndMushroomStewGrantsSated(GameTestHelper helper) {
        ServerPlayer soupEater = MockPlayers.serverPlayerInLevel(helper);
        eat(helper, soupEater, Items.BEETROOT_SOUP);
        MobEffectInstance diligent = soupEater.getEffect(CultivationEffects.DILIGENT);
        helper.assertTrue(diligent != null && diligent.getAmplifier() == 0, "beetroot soup grants Diligent I");
        helper.assertTrue(soupEater.getEffect(CultivationEffects.NIMBLE) == null
                && soupEater.getEffect(CultivationEffects.SATED) == null, "only Diligent is granted");

        ServerPlayer stewEater = MockPlayers.serverPlayerInLevel(helper);
        eat(helper, stewEater, Items.MUSHROOM_STEW);
        MobEffectInstance sated = stewEater.getEffect(CultivationEffects.SATED);
        helper.assertTrue(sated != null && sated.getAmplifier() == 0, "mushroom stew grants Sated I");
        helper.assertTrue(stewEater.getEffect(CultivationEffects.NIMBLE) == null
                && stewEater.getEffect(CultivationEffects.DILIGENT) == null, "only Sated is granted");
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void oneMealAtATimeReplacesTheBuff(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        eat(helper, player, Items.RABBIT_STEW);
        helper.assertTrue(player.getEffect(CultivationEffects.NIMBLE) != null, "Nimble applied first");

        eat(helper, player, Items.BEETROOT_SOUP);
        helper.assertTrue(player.getEffect(CultivationEffects.NIMBLE) == null,
                "the second meal removed Nimble before applying its own grant");
        helper.assertTrue(player.getEffect(CultivationEffects.DILIGENT) != null, "Diligent replaced it");
        helper.assertTrue(activeBuffCount(player) == 1, "exactly one meal buff is active — buffs never stack");
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void suspiciousStewGrantsOneBuffAtLevelTwo(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        eat(helper, player, Items.SUSPICIOUS_STEW);

        helper.assertTrue(activeBuffCount(player) == 1, "suspicious stew grants exactly one of the three buffs");
        MobEffectInstance granted = firstActiveBuff(player);
        helper.assertTrue(granted != null && granted.getAmplifier() == 1, "the granted buff is level II");
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void cakeSliceGrantsTheWholeTrio(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        player.getFoodData().setFoodLevel(6); // must be hungry to eat cake

        BlockPos cake = new BlockPos(1, 1, 1);
        helper.setBlock(cake, Blocks.CAKE);
        BlockPos abs = helper.absolutePos(cake);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
        helper.getLevel().getBlockState(abs).useWithoutItem(helper.getLevel(), player, hit);

        int cakeDuration = CultivationConfig.get().cakeBuffDurationTicks;
        for (Holder<MobEffect> effect : java.util.List.of(
                CultivationEffects.NIMBLE, CultivationEffects.DILIGENT, CultivationEffects.SATED)) {
            MobEffectInstance instance = player.getEffect(effect);
            helper.assertTrue(instance != null, "cake grants all three buffs");
            helper.assertTrue(instance.getAmplifier() == 0, "each cake buff is level I");
            helper.assertTrue(instance.getDuration() == cakeDuration,
                    "cake buffs last cakeBuffDurationTicks, got " + instance.getDuration());
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void satedSlowsRealExhaustion(GameTestHelper helper) {
        ServerPlayer plain = MockPlayers.serverPlayerInLevel(helper);
        plain.getAbilities().invulnerable = false; // causeFoodExhaustion no-ops while invulnerable
        float plainBefore = plain.getFoodData().getExhaustionLevel();
        plain.causeFoodExhaustion(2.0F);
        assertClose(helper, plain.getFoodData().getExhaustionLevel() - plainBefore, 2.0F,
                "without Sated, exhaustion accrues in full");

        ServerPlayer sated = MockPlayers.serverPlayerInLevel(helper);
        sated.getAbilities().invulnerable = false;
        sated.addEffect(new MobEffectInstance(CultivationEffects.SATED, 200, 0));
        float satedBefore = sated.getFoodData().getExhaustionLevel();
        sated.causeFoodExhaustion(2.0F);
        assertClose(helper, sated.getFoodData().getExhaustionLevel() - satedBefore, 1.8F,
                "Sated I cuts accrued exhaustion by 10%");
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void disabledConfigGrantsNoBuff(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        boolean saved = CultivationConfig.get().enableMealBuffs;
        try {
            CultivationConfig.get().enableMealBuffs = false;
            eat(helper, player, Items.RABBIT_STEW);
            helper.assertTrue(activeBuffCount(player) == 0, "no meal buff is granted while disabled");
            helper.succeed();
        } finally {
            CultivationConfig.get().enableMealBuffs = saved;
        }
    }

    private static int activeBuffCount(ServerPlayer player) {
        int count = 0;
        if (player.getEffect(CultivationEffects.NIMBLE) != null) count++;
        if (player.getEffect(CultivationEffects.DILIGENT) != null) count++;
        if (player.getEffect(CultivationEffects.SATED) != null) count++;
        return count;
    }

    private static MobEffectInstance firstActiveBuff(ServerPlayer player) {
        for (Holder<MobEffect> effect : java.util.List.of(
                CultivationEffects.NIMBLE, CultivationEffects.DILIGENT, CultivationEffects.SATED)) {
            MobEffectInstance instance = player.getEffect(effect);
            if (instance != null) {
                return instance;
            }
        }
        return null;
    }

    private static void eat(GameTestHelper helper, ServerPlayer player, Item item) {
        ServerLevel level = helper.getLevel();
        ItemStack stack = new ItemStack(item);
        FoodProperties food = stack.get(DataComponents.FOOD);
        helper.assertTrue(food != null, "test food " + item + " has a food component");
        player.eat(level, stack, food);
    }

    private static void assertClose(GameTestHelper helper, float actual, float expected, String message) {
        helper.assertTrue(Math.abs(actual - expected) < 1e-4,
                message + " (expected " + expected + ", got " + actual + ")");
    }
}
