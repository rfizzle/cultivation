package com.rfizzle.cultivation.gametest;

import com.rfizzle.cultivation.api.CultivationHarvestCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Test-side {@link CultivationHarvestCallback} listeners. Fabric events cannot
 * be unregistered, so the listeners register once for the whole gametest server
 * and act only on positions a test explicitly marks; tests unmark in a finally.
 *
 * <p>Registration order is the assertion order: the bonus listener mutates the
 * drops, the thrower proves per-listener error isolation, and the recorder
 * proves listeners after a throwing one still run and see the mutation.
 */
final class HarvestRecorder {
    record Recorded(BlockPos pos, ResourceLocation crop, List<ItemStack> drops, @Nullable Entity harvester) {
    }

    private static final AtomicBoolean REGISTERED = new AtomicBoolean();

    static final List<Recorded> RECORDS = new CopyOnWriteArrayList<>();
    static final Set<BlockPos> BONUS_POSITIONS = ConcurrentHashMap.newKeySet();
    static final Set<BlockPos> THROW_POSITIONS = ConcurrentHashMap.newKeySet();

    private HarvestRecorder() {
    }

    static void ensureRegistered() {
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }
        CultivationHarvestCallback.EVENT.register((level, pos, crop, drops, harvester) -> {
            if (BONUS_POSITIONS.contains(pos)) {
                drops.add(new ItemStack(Items.DIAMOND));
            }
        });
        CultivationHarvestCallback.EVENT.register((level, pos, crop, drops, harvester) -> {
            if (THROW_POSITIONS.contains(pos)) {
                throw new IllegalStateException("deliberate test-listener failure");
            }
        });
        CultivationHarvestCallback.EVENT.register((level, pos, crop, drops, harvester) ->
                RECORDS.add(new Recorded(pos.immutable(),
                        BuiltInRegistries.BLOCK.getKey(crop.getBlock()), List.copyOf(drops), harvester)));
    }
}
