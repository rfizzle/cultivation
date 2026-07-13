package com.rfizzle.cultivation.compat.wthit;

import com.rfizzle.cultivation.compat.common.CropProbeTooltip;
import mcp.mobius.waila.api.IBlockAccessor;
import mcp.mobius.waila.api.IBlockComponentProvider;
import mcp.mobius.waila.api.IDataProvider;
import mcp.mobius.waila.api.IDataWriter;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.IServerAccessor;
import mcp.mobius.waila.api.ITooltip;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;

/**
 * WTHIT adapter for the crop growth-modifier tooltip — pure delegation to
 * {@link CropProbeTooltip}. Crops have no block entity, so the target position
 * and state are derived from the hit result and a fresh block-state read.
 */
public enum CultivationWthitCropProvider implements IDataProvider<BlockEntity>, IBlockComponentProvider {
    INSTANCE;

    @Override
    public void appendData(IDataWriter data, IServerAccessor<BlockEntity> accessor, IPluginConfig config) {
        BlockPos pos = accessor.<BlockHitResult>getHitResult().getBlockPos();
        ServerLevel level = accessor.getLevel();
        CropProbeTooltip.writeServerData(data.raw(), level, pos, level.getBlockState(pos));
    }

    @Override
    public void appendBody(ITooltip tooltip, IBlockAccessor accessor, IPluginConfig config) {
        for (Component line : CropProbeTooltip.buildLines(accessor.getData().raw())) {
            tooltip.addLine(line);
        }
    }
}
