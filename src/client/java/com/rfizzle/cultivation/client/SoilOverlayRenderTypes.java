package com.rfizzle.cultivation.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.rfizzle.cultivation.Cultivation;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * The soil-overlay render types ({@code mc-world-render}): one translucent,
 * depth-tested, color-only pass per overlay texture, drawn in {@code
 * WorldRenderEvents.LAST} so it composes with Sodium, EBE, and Iris. Depth-tested
 * (LEQUAL) so overlays never show through walls, and depth-write off (COLOR_WRITE)
 * so a crack and its fleck compose without fighting each other's depth.
 *
 * <p>Extends {@link RenderType} solely to reach the {@code protected} state shards;
 * the class is never instantiated.
 */
public final class SoilOverlayRenderTypes extends RenderType {
    private static final ResourceLocation TIRED = Cultivation.id("textures/overlay/soil_tired.png");
    private static final ResourceLocation EXHAUSTED = Cultivation.id("textures/overlay/soil_exhausted.png");
    private static final ResourceLocation FERTILIZED = Cultivation.id("textures/overlay/soil_fertilized.png");
    private static final ResourceLocation ENRICHED = Cultivation.id("textures/overlay/soil_enriched.png");

    private static final RenderType TIRED_TYPE = create("soil_tired", TIRED);
    private static final RenderType EXHAUSTED_TYPE = create("soil_exhausted", EXHAUSTED);
    private static final RenderType FERTILIZED_TYPE = create("soil_fertilized", FERTILIZED);
    private static final RenderType ENRICHED_TYPE = create("soil_enriched", ENRICHED);

    private SoilOverlayRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
            boolean affectsCrumbling, boolean sortOnUpload, Runnable setupState, Runnable clearState) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
        throw new UnsupportedOperationException("state holder only");
    }

    public static RenderType tired() {
        return TIRED_TYPE;
    }

    public static RenderType exhausted() {
        return EXHAUSTED_TYPE;
    }

    public static RenderType fertilized() {
        return FERTILIZED_TYPE;
    }

    public static RenderType enriched() {
        return ENRICHED_TYPE;
    }

    private static RenderType create(String name, ResourceLocation texture) {
        CompositeState state = CompositeState.builder()
                .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionTexColorShader))
                .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setDepthTestState(LEQUAL_DEPTH_TEST)
                .setCullState(NO_CULL)
                .setWriteMaskState(COLOR_WRITE)
                .setLightmapState(NO_LIGHTMAP)
                .createCompositeState(false);
        return create(Cultivation.MOD_ID + ":" + name, DefaultVertexFormat.POSITION_TEX_COLOR,
                VertexFormat.Mode.QUADS, 256, false, true, state);
    }
}
