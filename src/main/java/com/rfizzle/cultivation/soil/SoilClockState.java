package com.rfizzle.cultivation.soil;

import com.rfizzle.cultivation.config.CultivationConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The per-level soil clock ({@code design/SPEC.md} §1): a persistent tick
 * counter that advances only while {@code enableSoilFertility} is true, so the
 * lazy recovery path can measure elapsed fallow time without accruing across
 * disabled spans. {@code SoilData.lastRecoveryCheck} stores values of this
 * clock, never {@code Level#getGameTime}.
 */
public class SoilClockState extends SavedData {
    private static final String STORAGE_KEY = "cultivation_soil_clock";
    private static final String TAG_TIME = "time";

    // Benign on 1.21.1: save() stamps the current DataVersion, so the fixer
    // short-circuits without touching the tag. Re-verify on every MC bump.
    private static final SavedData.Factory<SoilClockState> FACTORY =
            new SavedData.Factory<>(SoilClockState::new, SoilClockState::load, DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES);

    private long time;

    public static SoilClockState get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, STORAGE_KEY);
    }

    /** Wires the clock's advance into the end of every world tick. */
    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(level -> {
            if (CultivationConfig.get().enableSoilFertility) {
                get(level).advance();
            }
        });
    }

    public long time() {
        return time;
    }

    public void advance() {
        time++;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong(TAG_TIME, time);
        return tag;
    }

    private static SoilClockState load(CompoundTag tag, HolderLookup.Provider registries) {
        SoilClockState state = new SoilClockState();
        state.time = Math.max(0L, tag.getLong(TAG_TIME));
        return state;
    }
}
