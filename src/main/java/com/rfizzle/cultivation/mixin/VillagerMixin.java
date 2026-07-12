package com.rfizzle.cultivation.mixin;

import com.rfizzle.cultivation.config.CultivationConfig;
import com.rfizzle.cultivation.item.CultivationItems;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fertilizer upkeep, pickup half ({@code design/SPEC.md} §8): a farmer wants
 * Fertilizer the way it wants seeds, so the composter output it makes flows back
 * into its own fields. Only the farmer profession wants it, and only while the
 * fertilizing feature is on; every other villager and item is untouched.
 */
@Mixin(Villager.class)
abstract class VillagerMixin {
    @Inject(method = "wantsToPickUp", at = @At("HEAD"), cancellable = true)
    private void cultivation$wantFertilizer(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (!stack.is(CultivationItems.FERTILIZER)) {
            return;
        }
        Villager self = (Villager) (Object) this;
        if (self.getVillagerData().getProfession() != VillagerProfession.FARMER) {
            return;
        }
        CultivationConfig config = CultivationConfig.get();
        if (config.enableVillagerStewardship && config.enableVillagerFertilizing && config.enableFertilizer) {
            cir.setReturnValue(true);
        }
    }
}
