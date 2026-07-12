package com.rfizzle.cultivation.item;

import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the three scythes' combat/tool stats to the {@code design/SPEC.md} §7
 * table so a later edit to the tier or the {@code createAttributes} call can't
 * silently drift from the contract. Attack totals are the vanilla player base
 * (1.0 damage, 4.0 speed) plus the item's modifier.
 */
class ScytheStatsTest {
    private static final double BASE_ATTACK_DAMAGE = 1.0;
    private static final double BASE_ATTACK_SPEED = 4.0;

    @BeforeAll
    static void bootstrapVanillaRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static double modifierAmount(Item item, Holder<Attribute> attribute) {
        ItemAttributeModifiers mods = new ItemStack(item).get(DataComponents.ATTRIBUTE_MODIFIERS);
        return mods.modifiers().stream()
                .filter(entry -> entry.attribute().equals(attribute))
                .mapToDouble(entry -> entry.modifier().amount())
                .findFirst()
                .orElseThrow(() -> new AssertionError("scythe is missing an " + attribute + " modifier"));
    }

    private static void assertStats(Item scythe, int durability, double totalDamage, int enchantValue) {
        assertEquals(durability, new ItemStack(scythe).getMaxDamage(), "durability");
        assertEquals(enchantValue, scythe.getEnchantmentValue(), "enchantability");
        assertEquals(totalDamage, BASE_ATTACK_DAMAGE + modifierAmount(scythe, Attributes.ATTACK_DAMAGE), 1e-6,
                "total attack damage");
        assertEquals(1.6, BASE_ATTACK_SPEED + modifierAmount(scythe, Attributes.ATTACK_SPEED), 1e-6,
                "total attack speed");
    }

    @Test
    void ironScytheMatchesTheSpecTable() {
        assertStats(CultivationItems.IRON_SCYTHE, 250, 4.0, 14);
    }

    @Test
    void diamondScytheMatchesTheSpecTable() {
        assertStats(CultivationItems.DIAMOND_SCYTHE, 1561, 5.0, 10);
    }

    @Test
    void netheriteScytheMatchesTheSpecTable() {
        assertStats(CultivationItems.NETHERITE_SCYTHE, 2031, 6.0, 15);
    }
}
