package com.rfizzle.cultivation.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.rfizzle.cultivation.config.CultivationConfig;
import com.rfizzle.cultivation.soil.VillagerStewardship;
import com.rfizzle.cultivation.soil.WorkPositionThrottle;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.HarvestFarmland;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
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

    /**
     * The fallow gate's recheck throttle, one per behavior and so one per farmer.
     * Created on first use rather than initialized inline: Mixin does not merge
     * mixin constructors into the target, so a null default is the only field
     * initializer that is guaranteed to apply.
     */
    @Unique
    @Nullable
    private WorkPositionThrottle cultivation$fallowThrottle;

    /**
     * The Fertilizer upkeep's recheck throttle. Separate from the fallow gate's:
     * the two cache unrelated questions about the same block, so one interval must
     * not re-arm the other.
     */
    @Unique
    @Nullable
    private WorkPositionThrottle cultivation$doseThrottle;

    /**
     * Denies the replant branch on fallow-ineligible soil, reusing the previous
     * verdict for up to {@link WorkPositionThrottle#INTERVAL_TICKS} ticks. A denied
     * replant leaves the work block air, so the vanilla task never re-arms its own
     * throttle and retries the plant every tick; without this the gate would read
     * soil state 20 times a second for a farmer parked on resting ground.
     */
    @ModifyExpressionValue(
            method = "tick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/npc/Villager;hasFarmSeeds()Z"))
    private boolean cultivation$fallowGate(
            boolean hasSeeds,
            @Local(argsOnly = true) ServerLevel level,
            @Local(argsOnly = true) long gameTime) {
        if (!hasSeeds || aboveFarmlandPos == null) {
            return hasSeeds;
        }
        CultivationConfig config = CultivationConfig.get();
        if (!config.enableVillagerStewardship) {
            return hasSeeds;
        }
        if (cultivation$fallowThrottle == null) {
            cultivation$fallowThrottle = new WorkPositionThrottle();
        }
        long packedPos = aboveFarmlandPos.asLong();
        if (!cultivation$fallowThrottle.needsRecheck(packedPos, gameTime)) {
            return cultivation$fallowThrottle.cachedVerdict();
        }
        boolean verdict = VillagerStewardship.canReplant(level, aboveFarmlandPos.below(), config);
        cultivation$fallowThrottle.record(packedPos, gameTime, verdict);
        return verdict;
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

    /**
     * Refills a spent dose from the farmer's Fertilizer, at most once every
     * {@link WorkPositionThrottle#INTERVAL_TICKS}. This inject sits at the tail of
     * the tick, outside the vanilla task's own throttle, so without a gate the
     * dose check — an inventory scan and a soil read — would run 20 times a second
     * for as long as a Fertilizer-carrying farmer works the block, which SPEC §8
     * makes the designed steady state. Throttling only delays a refill: the dose
     * counter moves solely on harvest, through the drop choke point, so a skipped
     * tick can never miss or double a dose.
     */
    @Inject(method = "tick", at = @At("TAIL"))
    private void cultivation$fertilize(ServerLevel level, Villager villager, long gameTime, CallbackInfo ci) {
        if (aboveFarmlandPos == null || !aboveFarmlandPos.closerToCenterThan(villager.position(), 1.0)) {
            return;
        }
        CultivationConfig config = CultivationConfig.get();
        if (!config.enableVillagerStewardship || !config.enableVillagerFertilizing) {
            return;
        }
        if (cultivation$doseThrottle == null) {
            cultivation$doseThrottle = new WorkPositionThrottle();
        }
        long packedPos = aboveFarmlandPos.asLong();
        if (!cultivation$doseThrottle.needsRecheck(packedPos, gameTime)) {
            return;
        }
        cultivation$doseThrottle.record(packedPos, gameTime);
        VillagerStewardship.tryDose(level, aboveFarmlandPos.below(), villager.getInventory());
    }
}
