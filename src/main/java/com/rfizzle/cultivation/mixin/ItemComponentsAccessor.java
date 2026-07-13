package com.rfizzle.cultivation.mixin;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Read/write access to an {@link Item}'s registered default {@link
 * DataComponentMap}. Fabric exposes no runtime hook for an already-registered
 * vanilla item's max stack size, so {@link
 * com.rfizzle.cultivation.meal.BowlFoodStacking} rebuilds the bowl foods'
 * component maps through this accessor once at init. The {@code components}
 * field is {@code private final}; {@link Mutable} lifts the {@code final} for
 * the setter.
 */
@Mixin(Item.class)
public interface ItemComponentsAccessor {
    @Accessor("components")
    DataComponentMap cultivation$getComponents();

    @Mutable
    @Accessor("components")
    void cultivation$setComponents(DataComponentMap components);
}
