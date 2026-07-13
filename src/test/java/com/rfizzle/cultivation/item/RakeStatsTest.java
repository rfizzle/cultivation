package com.rfizzle.cultivation.item;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the iron rake's tool stats ({@code design/SPEC.md} §7): it draws the iron
 * tier's durability and enchantability, and — being a tool, not a weapon —
 * carries no attack-attribute modifiers, so a later edit to the item can't
 * silently turn it into a combat item or drift its durability from the contract.
 */
class RakeStatsTest {
    @BeforeAll
    static void bootstrapVanillaRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void ironRakeMatchesTheIronTier() {
        assertEquals(250, new ItemStack(CultivationItems.IRON_RAKE).getMaxDamage(), "durability");
        assertEquals(14, CultivationItems.IRON_RAKE.getEnchantmentValue(), "enchantability");
    }

    @Test
    void ironRakeCarriesNoAttackModifiers() {
        ItemAttributeModifiers mods = new ItemStack(CultivationItems.IRON_RAKE).get(DataComponents.ATTRIBUTE_MODIFIERS);
        assertTrue(mods == null || mods.modifiers().isEmpty(),
                "the rake is a tool, not a weapon — it must add no attribute modifiers");
    }
}
