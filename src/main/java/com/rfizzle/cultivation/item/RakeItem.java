package com.rfizzle.cultivation.item;

import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TieredItem;

/**
 * The sowing rake ({@code design/SPEC.md} §7). A plain {@link TieredItem} so it
 * draws its durability, iron repair, and enchantability straight from the vanilla
 * {@link Tier}; it carries no attack attributes, so as a weapon it is an ordinary
 * item. The 3×3 broadcast sow is a use behavior wired in by
 * {@code BroadcastSowingHandler}, not item logic, so this class carries no use
 * override — it is the marker type that handler and any sibling integration key
 * off, the planting counterpart to {@link ScytheItem}.
 */
public class RakeItem extends TieredItem {
    public RakeItem(Tier tier, Properties properties) {
        super(tier, properties);
    }
}
