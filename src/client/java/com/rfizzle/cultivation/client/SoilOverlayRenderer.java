package com.rfizzle.cultivation.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rfizzle.cultivation.attachment.SoilStore;
import com.rfizzle.cultivation.config.CultivationConfig;
import com.rfizzle.cultivation.soil.SoilBand;
import com.rfizzle.cultivation.soil.SoilOverlayFlags;
import com.rfizzle.cultivation.soil.SoilOverlayMath;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Draws soil condition overlays as flat translucent quads on farmland top faces
 * ({@code design/SPEC.md} §1), in the {@code WorldRenderEvents.LAST} post-pass so
 * it never touches chunk/block-entity rendering and composes with Sodium, EBE, and
 * Iris ({@code mc-world-render}). Unlike a camera-facing billboard, the quad is
 * built in world orientation — no rotation toward the camera — so it lies on the
 * ground. Cracks flush before flecks so investment overlays compose on top.
 */
public final class SoilOverlayRenderer {
    /** Farmland's top face sits at 15/16; lift the quad a hair above it to avoid z-fighting. */
    private static final float SURFACE_Y = 0.9375F + 0.02F;

    private SoilOverlayRenderer() {
    }

    public static void register() {
        WorldRenderEvents.LAST.register(SoilOverlayRenderer::render);
    }

    private static void render(WorldRenderContext context) {
        CultivationConfig config = CultivationConfig.get();
        ClientLevel level = context.world();
        if (!config.showSoilOverlays || ClientSoilOverlayData.isEmpty() || level == null) {
            return;
        }
        if (!(context.consumers() instanceof MultiBufferSource.BufferSource bufferSource)) {
            return;
        }

        Camera camera = context.camera();
        Vec3 cam = camera.getPosition();
        PoseStack pose = context.matrixStack();
        if (pose == null) {
            return;
        }
        Matrix4f matrix = pose.last().pose();
        double maxDistance = config.soilOverlayRenderDistance;
        BlockPos.MutableBlockPos scratch = new BlockPos.MutableBlockPos();

        ClientSoilOverlayData.forEach((chunkPos, packedPos, flags) -> {
            ChunkPos chunk = new ChunkPos(chunkPos);
            int blockX = chunk.getMinBlockX() + SoilStore.unpackX(packedPos);
            int blockY = SoilStore.unpackY(packedPos);
            int blockZ = chunk.getMinBlockZ() + SoilStore.unpackZ(packedPos);

            double dx = blockX + 0.5 - cam.x;
            double dy = blockY + 0.5 - cam.y;
            double dz = blockZ + 0.5 - cam.z;
            if (!SoilOverlayMath.withinRenderDistanceSq(dx, dy, dz, maxDistance)) {
                return;
            }

            int light = level.getMaxLocalRawBrightness(scratch.set(blockX, blockY + 1, blockZ));
            int shade = (int) (255.0F * SoilOverlayMath.brightnessFactor(light));

            SoilBand band = SoilOverlayFlags.band(flags);
            if (band == SoilBand.TIRED) {
                emitQuad(matrix, bufferSource.getBuffer(SoilOverlayRenderTypes.tired()), cam, blockX, blockY, blockZ, shade);
            } else if (band == SoilBand.EXHAUSTED) {
                emitQuad(matrix, bufferSource.getBuffer(SoilOverlayRenderTypes.exhausted()), cam, blockX, blockY, blockZ, shade);
            }
            if (SoilOverlayFlags.hasDose(flags)) {
                emitQuad(matrix, bufferSource.getBuffer(SoilOverlayRenderTypes.fertilized()), cam, blockX, blockY, blockZ, shade);
            }
            if (SoilOverlayFlags.isEnriched(flags)) {
                emitQuad(matrix, bufferSource.getBuffer(SoilOverlayRenderTypes.enriched()), cam, blockX, blockY, blockZ, shade);
            }
        });

        // Flush cracks first, then flecks, so a Fertilizer/enriched fleck composes on top.
        bufferSource.endBatch(SoilOverlayRenderTypes.tired());
        bufferSource.endBatch(SoilOverlayRenderTypes.exhausted());
        bufferSource.endBatch(SoilOverlayRenderTypes.fertilized());
        bufferSource.endBatch(SoilOverlayRenderTypes.enriched());
    }

    private static void emitQuad(Matrix4f matrix, VertexConsumer buffer, Vec3 cam,
            int blockX, int blockY, int blockZ, int shade) {
        float x0 = (float) (blockX - cam.x);
        float x1 = (float) (blockX + 1 - cam.x);
        float z0 = (float) (blockZ - cam.z);
        float z1 = (float) (blockZ + 1 - cam.z);
        float y = (float) (blockY + SURFACE_Y - cam.y);
        buffer.addVertex(matrix, x0, y, z0).setUv(0.0F, 0.0F).setColor(shade, shade, shade, 255);
        buffer.addVertex(matrix, x0, y, z1).setUv(0.0F, 1.0F).setColor(shade, shade, shade, 255);
        buffer.addVertex(matrix, x1, y, z1).setUv(1.0F, 1.0F).setColor(shade, shade, shade, 255);
        buffer.addVertex(matrix, x1, y, z0).setUv(1.0F, 0.0F).setColor(shade, shade, shade, 255);
    }
}
