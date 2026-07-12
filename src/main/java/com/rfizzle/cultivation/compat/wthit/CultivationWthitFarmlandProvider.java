package com.rfizzle.cultivation.compat.wthit;

import com.rfizzle.cultivation.compat.common.FarmlandProbeTooltip;
import mcp.mobius.waila.api.IBlockAccessor;
import mcp.mobius.waila.api.IBlockComponentProvider;
import mcp.mobius.waila.api.IDataProvider;
import mcp.mobius.waila.api.IDataWriter;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.IServerAccessor;
import mcp.mobius.waila.api.ITooltip;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;

/**
 * WTHIT adapter for the farmland soil tooltip — pure delegation to
 * {@link FarmlandProbeTooltip}. Farmland has no block entity, so the provider is
 * typed to the loosest legal bound ({@code BlockEntity}) and derives the target
 * position from the hit result rather than {@code getTarget()} (which resolves
 * through a block-entity lookup and is null here).
 */
public enum CultivationWthitFarmlandProvider implements IDataProvider<BlockEntity>, IBlockComponentProvider {
    INSTANCE;

    @Override
    public void appendData(IDataWriter data, IServerAccessor<BlockEntity> accessor, IPluginConfig config) {
        BlockPos pos = accessor.<BlockHitResult>getHitResult().getBlockPos();
        FarmlandProbeTooltip.writeServerData(data.raw(), accessor.getLevel(), pos);
    }

    @Override
    public void appendBody(ITooltip tooltip, IBlockAccessor accessor, IPluginConfig config) {
        for (Component line : FarmlandProbeTooltip.buildLines(accessor.getData().raw())) {
            tooltip.addLine(line);
        }
    }
}
