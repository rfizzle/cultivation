package com.rfizzle.cultivation.attachment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A chunk's soil entries, keyed by packed in-chunk position. This is the value
 * of the persistent chunk attachment ({@link CultivationAttachments#SOIL}); all
 * reads and writes go through {@link SoilStores}, which owns settling, default
 * eviction, and chunk dirtying.
 *
 * <p>Serializes as a list of entries sorted by packed position so chunk saves
 * are deterministic. Entries that hold all-default values are dropped on both
 * write ({@link #put}) and read (a hand-edited default entry evicts on load).
 */
public final class SoilStore {
    /** Shifts absolute Y into an unsigned 12-bit field (world Y is hard-limited to −2032..2031). */
    private static final int Y_OFFSET = 2048;

    private final Int2ObjectMap<SoilData> entries = new Int2ObjectOpenHashMap<>();

    private record Entry(int pos, SoilData data) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("pos").forGetter(Entry::pos),
                SoilData.CODEC.fieldOf("data").forGetter(Entry::data)
        ).apply(instance, Entry::new));
    }

    public static final Codec<SoilStore> CODEC = Entry.CODEC.listOf()
            .xmap(SoilStore::fromEntries, SoilStore::toSortedEntries);

    /** Packs an absolute position into an in-chunk key: x | z&lt;&lt;4 | (y+2048)&lt;&lt;8. */
    public static int pack(BlockPos pos) {
        return (pos.getX() & 15) | ((pos.getZ() & 15) << 4) | ((pos.getY() + Y_OFFSET) << 8);
    }

    public static int unpackX(int key) {
        return key & 15;
    }

    public static int unpackZ(int key) {
        return (key >> 4) & 15;
    }

    public static int unpackY(int key) {
        return (key >>> 8) - Y_OFFSET;
    }

    @Nullable
    public SoilData get(int key) {
        return entries.get(key);
    }

    /** Stores {@code data}, or evicts the entry when it has returned to all-default values. */
    public void put(int key, SoilData data) {
        if (data.isDefault()) {
            entries.remove(key);
        } else {
            entries.put(key, data);
        }
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public int size() {
        return entries.size();
    }

    private static SoilStore fromEntries(List<Entry> list) {
        SoilStore store = new SoilStore();
        for (Entry entry : list) {
            store.put(entry.pos(), entry.data());
        }
        return store;
    }

    private List<Entry> toSortedEntries() {
        List<Entry> list = new ArrayList<>(entries.size());
        entries.forEach((key, data) -> list.add(new Entry(key, data)));
        list.sort(Comparator.comparingInt(Entry::pos));
        return list;
    }
}
