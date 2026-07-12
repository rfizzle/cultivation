package com.rfizzle.cultivation.item;

import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TieredItem;

/**
 * The harvesting scythe ({@code design/SPEC.md} §7). A plain {@link TieredItem}
 * so it draws durability and enchantability straight from the vanilla
 * {@link Tier} and — unlike {@code SwordItem} — never triggers the melee
 * sweep-attack: as a weapon it hits one target like any tool. The 3×3 harvest is
 * a block-break behavior wired in by {@code ScytheHarvestHandler}, not item
 * logic, so this class carries no use or combat overrides; it is the marker type
 * that handler and any sibling integration key off.
 */
public class ScytheItem extends TieredItem {
    public ScytheItem(Tier tier, Properties properties) {
        super(tier, properties);
    }
}
