package com.rfizzle.cultivation.compat.wthit;

import mcp.mobius.waila.api.ICommonRegistrar;
import mcp.mobius.waila.api.IWailaCommonPlugin;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;

/**
 * WTHIT server-side plugin (discovered via {@code waila_plugins.json}) — registers
 * the soil and crop data providers on their vanilla block classes. WTHIT reads the
 * manifest itself, so this class only loads when WTHIT is present; no mod-loaded
 * guard is needed.
 */
public final class CultivationWthitCommonPlugin implements IWailaCommonPlugin {
    @Override
    public void register(ICommonRegistrar registrar) {
        registrar.blockData(CultivationWthitFarmlandProvider.INSTANCE, FarmBlock.class);
        registrar.blockData(CultivationWthitFarmlandProvider.INSTANCE, NetherWartBlock.class);
        registrar.blockData(CultivationWthitFarmlandProvider.INSTANCE, SweetBerryBushBlock.class);
        registrar.blockData(CultivationWthitCropProvider.INSTANCE, CropBlock.class);
        registrar.blockData(CultivationWthitCropProvider.INSTANCE, StemBlock.class);
        registrar.blockData(CultivationWthitCropProvider.INSTANCE, AttachedStemBlock.class);
        registrar.blockData(CultivationWthitCropProvider.INSTANCE, PitcherCropBlock.class);
    }
}
