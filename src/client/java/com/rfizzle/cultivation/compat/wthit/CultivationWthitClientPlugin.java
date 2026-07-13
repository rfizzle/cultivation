package com.rfizzle.cultivation.compat.wthit;

import mcp.mobius.waila.api.IClientRegistrar;
import mcp.mobius.waila.api.IWailaClientPlugin;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;

/**
 * WTHIT client-side plugin (the {@code client} entry in {@code waila_plugins.json})
 * — registers the tooltip body components that render the data the common plugin's
 * providers packed. Split from the common plugin because WTHIT marks its client
 * registrar client-only; both reference the shared provider enums in the main
 * source set.
 */
public final class CultivationWthitClientPlugin implements IWailaClientPlugin {
    @Override
    public void register(IClientRegistrar registrar) {
        registrar.body(CultivationWthitFarmlandProvider.INSTANCE, FarmBlock.class);
        registrar.body(CultivationWthitFarmlandProvider.INSTANCE, NetherWartBlock.class);
        registrar.body(CultivationWthitFarmlandProvider.INSTANCE, SweetBerryBushBlock.class);
        registrar.body(CultivationWthitCropProvider.INSTANCE, CropBlock.class);
        registrar.body(CultivationWthitCropProvider.INSTANCE, StemBlock.class);
        registrar.body(CultivationWthitCropProvider.INSTANCE, AttachedStemBlock.class);
        registrar.body(CultivationWthitCropProvider.INSTANCE, PitcherCropBlock.class);
    }
}
