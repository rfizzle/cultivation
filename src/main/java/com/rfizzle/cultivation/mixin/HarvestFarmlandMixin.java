package com.rfizzle.cultivation.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.rfizzle.cultivation.config.CultivationConfig;
import com.rfizzle.cultivation.soil.VillagerStewardship;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.HarvestFarmland;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Villager field stewardship ({@code design/SPEC.md} §8): three edits to the
 * farmer's existing farmland work task, gated live on {@code enableVillagerStewardship}.
 * Harvesting a mature crop is never touched — it flows through the harvest choke
 * point like any other actor. Nothing about villager identity is changed.
 *
 * <ul>
 *   <li>the replant branch's {@code hasFarmSeeds} guard is denied when the target
 *       soil is fallow-ineligible, so a tired block rests (with the hysteresis
 *       latch settled at the same time);</li>
 *   <li>the seed scan's plantable-tag test rejects a seed matching the block's
 *       last crop while a differing seed is on hand, so the farmer rotates;</li>
 *   <li>a spent farmland dose is refilled from the farmer's Fertilizer at the end
 *       of a work tick.</li>
 * </ul>
 */
@Mixin(HarvestFarmland.class)
abstract class HarvestFarmlandMixin {
    @Shadow
    @Nullable
    private BlockPos aboveFarmlandPos;

    @ModifyExpressionValue(
            method = "tick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/npc/Villager;hasFarmSeeds()Z"))
    private boolean cultivation$fallowGate(boolean hasSeeds, @Local(argsOnly = true) ServerLevel level) {
        if (!hasSeeds || aboveFarmlandPos == null) {
            return hasSeeds;
        }
        CultivationConfig config = CultivationConfig.get();
        if (!config.enableVillagerStewardship) {
            return hasSeeds;
        }
        return VillagerStewardship.canReplant(level, aboveFarmlandPos.below(), config);
    }

    @ModifyExpressionValue(
            method = "tick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean cultivation$rotationGate(
            boolean plantable,
            @Local(argsOnly = true) ServerLevel level,
            @Local(argsOnly = true) Villager villager,
            @Local ItemStack seed) {
        if (!plantable || aboveFarmlandPos == null || !CultivationConfig.get().enableVillagerStewardship) {
            return plantable;
        }
        return VillagerStewardship.acceptSeedForRotation(
                level, aboveFarmlandPos.below(), seed, villager.getInventory());
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void cultivation$fertilize(ServerLevel level, Villager villager, long gameTime, CallbackInfo ci) {
        if (aboveFarmlandPos == null || !aboveFarmlandPos.closerToCenterThan(villager.position(), 1.0)) {
            return;
        }
        CultivationConfig config = CultivationConfig.get();
        if (!config.enableVillagerStewardship || !config.enableVillagerFertilizing) {
            return;
        }
        VillagerStewardship.tryDose(level, aboveFarmlandPos.below(), villager.getInventory());
    }
}
