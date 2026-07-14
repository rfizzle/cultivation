package com.rfizzle.cultivation.compat.common;

import com.rfizzle.cultivation.config.CultivationConfig;
import com.rfizzle.cultivation.soil.BeePollination;
import com.rfizzle.cultivation.soil.Polyculture;
import com.rfizzle.cultivation.soil.SoilGrowth;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The viewer-agnostic crop probe-tooltip surface (mc-probe-tooltips): reports the
 * combined growth-speed modifier a supported crop is currently receiving and
 * whether the polyculture and bee-pollination bonuses are active (SPEC §1–§2).
 * Every value depends on server config and live world reads, so they are computed
 * in the server-side writer; the formatter stays a pure tag → lines mapping. No
 * Jade or WTHIT types.
 */
public final class CropProbeTooltip {
    static final String KEY_PRESENT = "cultivation:crop_present";
    static final String KEY_GROWTH = "cultivation:growth";
    static final String KEY_POLYCULTURE = "cultivation:polyculture";
    static final String KEY_SNIFFER = "cultivation:sniffer";
    static final String KEY_BEE = "cultivation:bee";

    private CropProbeTooltip() {
    }

    /**
     * Server side. Writes nothing when both soil systems are off or the target is
     * not a supported crop (the presence gate), so the tooltip stays empty on
     * every other block a broad registration covers.
     */
    public static void writeServerData(CompoundTag tag, ServerLevel level, BlockPos pos, BlockState state) {
        CultivationConfig config = CultivationConfig.get();
        if (!config.enableSoilFertility && !config.enablePolyculture && !config.enableBeePollination) {
            return;
        }
        if (Polyculture.cropIdentity(state) == null) {
            return;
        }
        tag.putBoolean(KEY_PRESENT, true);
        tag.putFloat(KEY_GROWTH, SoilGrowth.multiplierAt(level, pos, state));
        tag.putBoolean(KEY_POLYCULTURE, Polyculture.multiplierAt(level, pos, state) > 1.0F);
        tag.putBoolean(KEY_SNIFFER, Polyculture.snifferPremiumActiveAt(level, pos, state));
        tag.putBoolean(KEY_BEE, BeePollination.multiplierAt(level, pos) > 1.0F);
    }

    /** Client side. Pure tag → lines. */
    public static List<Component> buildLines(CompoundTag tag) {
        List<Component> lines = new ArrayList<>();
        if (!tag.getBoolean(KEY_PRESENT)) {
            return lines;
        }
        lines.add(Component.translatable("tooltip.cultivation.crop.growth",
                String.format(Locale.ROOT, "%.2f", tag.getFloat(KEY_GROWTH))));
        if (tag.getBoolean(KEY_POLYCULTURE)) {
            lines.add(Component.translatable("tooltip.cultivation.crop.polyculture"));
        }
        if (tag.getBoolean(KEY_SNIFFER)) {
            lines.add(Component.translatable("tooltip.cultivation.crop.sniffer"));
        }
        if (tag.getBoolean(KEY_BEE)) {
            lines.add(Component.translatable("tooltip.cultivation.crop.bees"));
        }
        return lines;
    }
}
