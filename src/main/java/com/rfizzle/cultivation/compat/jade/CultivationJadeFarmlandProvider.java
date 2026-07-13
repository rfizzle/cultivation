package com.rfizzle.cultivation.compat.jade;

import com.rfizzle.cultivation.Cultivation;
import com.rfizzle.cultivation.compat.common.FarmlandProbeTooltip;
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
 * Jade adapter for the farmland soil tooltip — pure delegation to
 * {@link FarmlandProbeTooltip}, so the Jade and WTHIT overlays render the same
 * lines. Farmland carries no block entity; this keys on {@code FarmBlock} and
 * reads position and level straight off the accessor.
 */
public enum CultivationJadeFarmlandProvider implements IServerDataProvider<BlockAccessor>, IBlockComponentProvider {
    INSTANCE;

    private static final ResourceLocation UID = Cultivation.id("farmland");

    @Override
    public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
        if (accessor.getLevel() instanceof ServerLevel level) {
            FarmlandProbeTooltip.writeServerData(tag, level, accessor.getPosition());
        }
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        for (Component line : FarmlandProbeTooltip.buildLines(accessor.getServerData())) {
            tooltip.add(line);
        }
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
