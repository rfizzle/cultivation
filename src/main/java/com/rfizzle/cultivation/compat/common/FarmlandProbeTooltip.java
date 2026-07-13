package com.rfizzle.cultivation.compat.common;

import com.rfizzle.cultivation.api.CultivationAPI;
import com.rfizzle.cultivation.api.SoilInfo;
import com.rfizzle.cultivation.command.CommandText;
import com.rfizzle.cultivation.config.CultivationConfig;
import com.rfizzle.cultivation.soil.SoilBand;
import com.rfizzle.cultivation.soil.SoilMath;
import com.rfizzle.cultivation.soil.SupportedCrops;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The viewer-agnostic soil probe-tooltip surface (mc-probe-tooltips): a
 * server-side writer that packs a looked-at soil block's state into the probe's
 * tag, and a pure formatter that turns that tag back into tooltip lines. Soil is
 * farmland, or a second-wave crop's ground (soul sand under nether wart, dirt
 * under a sweet berry bush) — looking at either the ground or the crop standing on
 * it reads the same entry. Holds no Jade or WTHIT types, so a missing viewer never
 * class-loads it and both adapters render an identical tooltip by construction.
 * Mirrors the data set and whole-percent formatting of {@code /cultivation soil}
 * (SPEC §1).
 */
public final class FarmlandProbeTooltip {
    // Namespaced so the shared per-target tag can't collide with another mod's keys; the
    // presence flag keeps a broad block registration inert on every non-farmland target.
    static final String KEY_PRESENT = "cultivation:soil_present";
    static final String KEY_FERTILITY = "cultivation:fertility";
    static final String KEY_BAND = "cultivation:band";
    static final String KEY_ENRICHED = "cultivation:enriched";
    static final String KEY_FERTILIZER = "cultivation:fertilizer";
    static final String KEY_DOSE = "cultivation:dose";
    static final String KEY_LAST_CROP = "cultivation:last_crop";

    private FarmlandProbeTooltip() {
    }

    /**
     * Server side. Writes nothing — no presence flag, so an empty tooltip — when
     * the soil system is off or the target is neither soil nor a crop standing on
     * soil. The fertility band is classified here (it needs the server's
     * {@code tiredThreshold}) so the formatter stays a pure tag → lines mapping.
     */
    public static void writeServerData(CompoundTag tag, ServerLevel level, BlockPos pos) {
        CultivationConfig config = CultivationConfig.get();
        if (!config.enableSoilFertility) {
            return;
        }
        BlockPos soilPos = resolveSoilPos(level, pos, config.enableNonFarmlandSoil);
        if (soilPos == null) {
            return;
        }
        Optional<SoilInfo> info = CultivationAPI.getSoilInfo(level, soilPos);
        if (info.isEmpty()) {
            return;
        }
        SoilInfo soil = info.get();
        SoilBand band = SoilMath.band(soil.fertility(), config.tiredThreshold);
        tag.putBoolean(KEY_PRESENT, true);
        tag.putFloat(KEY_FERTILITY, soil.fertility());
        tag.putString(KEY_BAND, band.name());
        tag.putInt(KEY_ENRICHED, soil.enrichedChance());
        tag.putInt(KEY_FERTILIZER, soil.fertilizerRemaining());
        tag.putInt(KEY_DOSE, config.fertilizerDoseHarvests);
        soil.lastCrop().ifPresent(id -> tag.putString(KEY_LAST_CROP, id.toString()));
    }

    /** The soil position for a looked-at block: the block itself if it is soil, else the soil ground below a crop. */
    @Nullable
    private static BlockPos resolveSoilPos(ServerLevel level, BlockPos pos, boolean includeSecondWave) {
        if (SupportedCrops.isTrackedSoilGround(
                level.getBlockState(pos), level.getBlockState(pos.above()), includeSecondWave)) {
            return pos;
        }
        BlockPos below = pos.below();
        if (SupportedCrops.isTrackedSoilGround(
                level.getBlockState(below), level.getBlockState(pos), includeSecondWave)) {
            return below;
        }
        return null;
    }

    /** Client side. Pure tag → lines; the tag crossed the network, so treat it as data. */
    public static List<Component> buildLines(CompoundTag tag) {
        List<Component> lines = new ArrayList<>();
        if (!tag.getBoolean(KEY_PRESENT)) {
            return lines;
        }
        lines.add(Component.translatable("tooltip.cultivation.soil.fertility",
                CommandText.percent(tag.getFloat(KEY_FERTILITY)), bandName(tag.getString(KEY_BAND))));
        int enriched = tag.getInt(KEY_ENRICHED);
        if (enriched > 0) {
            lines.add(Component.translatable("tooltip.cultivation.soil.enriched", enriched));
        }
        int fertilizer = tag.getInt(KEY_FERTILIZER);
        if (fertilizer > 0) {
            lines.add(Component.translatable("tooltip.cultivation.soil.fertilizer", fertilizer, tag.getInt(KEY_DOSE)));
        }
        if (tag.contains(KEY_LAST_CROP)) {
            lines.add(Component.translatable("tooltip.cultivation.soil.crop", cropName(tag.getString(KEY_LAST_CROP))));
        }
        return lines;
    }

    /** The band's display name, falling back to the raw token if the wire value is unknown. */
    private static Component bandName(String raw) {
        for (SoilBand band : SoilBand.values()) {
            if (band.name().equals(raw)) {
                return Component.translatable("tooltip.cultivation.soil.band." + raw.toLowerCase(Locale.ROOT));
            }
        }
        return Component.literal(raw);
    }

    /** The crop block's display name, falling back to the raw id when it no longer resolves. */
    private static Component cropName(String raw) {
        ResourceLocation id = ResourceLocation.tryParse(raw);
        if (id != null && BuiltInRegistries.BLOCK.containsKey(id)) {
            return BuiltInRegistries.BLOCK.get(id).getName();
        }
        return Component.literal(raw);
    }
}
