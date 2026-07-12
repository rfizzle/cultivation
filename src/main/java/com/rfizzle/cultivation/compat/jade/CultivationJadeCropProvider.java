package com.rfizzle.cultivation.compat.jade;

import com.rfizzle.cultivation.Cultivation;
import com.rfizzle.cultivation.compat.common.CropProbeTooltip;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade adapter for the crop growth-modifier tooltip — pure delegation to
 * {@link CropProbeTooltip}. The block state comes straight off the accessor; the
 * writer's own crop check keeps it inert on anything registered but non-crop.
 */
public enum CultivationJadeCropProvider implements IServerDataProvider<BlockAccessor>, IBlockComponentProvider {
    INSTANCE;

    private static final ResourceLocation UID = Cultivation.id("crop");

    @Override
    public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
        if (accessor.getLevel() instanceof ServerLevel level) {
            CropProbeTooltip.writeServerData(tag, level, accessor.getPosition(), accessor.getBlockState());
        }
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        for (Component line : CropProbeTooltip.buildLines(accessor.getServerData())) {
            tooltip.add(line);
        }
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
