package com.rfizzle.cultivation.compat.jade;

import com.rfizzle.cultivation.Cultivation;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Jade plugin discovery (the {@code jade} entrypoint in {@code fabric.mod.json}).
 * Registers the soil tooltip on {@code FarmBlock} and the growth tooltip on each
 * crop family — none of these vanilla blocks has a block entity, and Jade's block
 * data providers key on the block class directly. Registers per concrete crop
 * class since the families share no crop-specific superclass.
 */
@WailaPlugin(Cultivation.MOD_ID)
public final class CultivationJadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(CultivationJadeFarmlandProvider.INSTANCE, FarmBlock.class);
        // The second-wave crops carry the soil line on the crop block — their ground is read below.
        registration.registerBlockDataProvider(CultivationJadeFarmlandProvider.INSTANCE, NetherWartBlock.class);
        registration.registerBlockDataProvider(CultivationJadeFarmlandProvider.INSTANCE, SweetBerryBushBlock.class);
        registration.registerBlockDataProvider(CultivationJadeCropProvider.INSTANCE, CropBlock.class);
        registration.registerBlockDataProvider(CultivationJadeCropProvider.INSTANCE, StemBlock.class);
        registration.registerBlockDataProvider(CultivationJadeCropProvider.INSTANCE, AttachedStemBlock.class);
        registration.registerBlockDataProvider(CultivationJadeCropProvider.INSTANCE, PitcherCropBlock.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(CultivationJadeFarmlandProvider.INSTANCE, FarmBlock.class);
        registration.registerBlockComponent(CultivationJadeFarmlandProvider.INSTANCE, NetherWartBlock.class);
        registration.registerBlockComponent(CultivationJadeFarmlandProvider.INSTANCE, SweetBerryBushBlock.class);
        registration.registerBlockComponent(CultivationJadeCropProvider.INSTANCE, CropBlock.class);
        registration.registerBlockComponent(CultivationJadeCropProvider.INSTANCE, StemBlock.class);
        registration.registerBlockComponent(CultivationJadeCropProvider.INSTANCE, AttachedStemBlock.class);
        registration.registerBlockComponent(CultivationJadeCropProvider.INSTANCE, PitcherCropBlock.class);
    }
}
