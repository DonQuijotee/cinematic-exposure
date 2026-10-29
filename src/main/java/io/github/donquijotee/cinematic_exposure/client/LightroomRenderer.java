package io.github.donquijotee.cinematic_exposure.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.dyslexic.cinematic.client.ItemPlacer;
import io.github.donquijotee.cinematic_exposure.ClientConfig;
import io.github.mortuusars.exposure.world.block.LightroomBlock;
import io.github.mortuusars.exposure.world.block.entity.LightroomBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.Vec3;

/** Reads Exposure's already-synchronized BE inventory, even without an open menu or active scene. */
public final class LightroomRenderer implements BlockEntityRenderer<LightroomBlockEntity> {
    public LightroomRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(LightroomBlockEntity entity) {
        return new net.minecraft.world.phys.AABB(entity.getBlockPos()).expandTowards(0, 0.2, 0);
    }

    @Override
    public void render(LightroomBlockEntity entity, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        if (!ClientConfig.LIGHTROOM.get() || entity.isRemoved()) return;
        var pos = entity.getBlockPos();
        var facing = entity.getBlockState().getValue(LightroomBlock.FACING);
        if (entity.getLevel() != null) light = net.minecraft.client.renderer.LevelRenderer.getLightColor(entity.getLevel(), pos.above());
        // BER pose already includes block translation; ItemPlacer subtracts this origin, not the camera.
        Vec3 origin = Vec3.atLowerCornerOf(pos);
        var station = dev.dyslexic.cinematic.client.Scene.station(LightroomStation.class);
        for (int slot = 0; slot < entity.getContainerSize(); slot++) {
            if (slot == 0 && station != null && station.movingFilm(pos, entity.getItem(slot))) continue;
            ItemPlacer.renderOne(pose, buffers, origin, entity.getItem(slot),
                    LightroomLayout.point(pos, facing, slot), facing, pos.asLong() + slot, 0.72f, 0, light);
        }
        renderPhotograph(entity, pose, buffers, light);
    }

    private void renderPhotograph(LightroomBlockEntity entity, PoseStack pose, MultiBufferSource buffers, int light) {
        var frame = entity.getSelectedFrame();
        // With no film, show the real top output photograph, if any. Never mutate the stored stack.
        if (frame == null) {
            var output = entity.getItem(6);
            if (output.getItem() instanceof io.github.mortuusars.exposure.world.item.StackedPhotographsItem stack) {
                var photos = stack.getPhotographs(output);
                if (!photos.isEmpty()) output = photos.getItemUnsafe(photos.size() - 1);
            }
            if (output.getItem() instanceof io.github.mortuusars.exposure.world.item.PhotographItem photo)
                frame = photo.getFrame(output);
        }
        if (frame == null) return;
        var image = io.github.mortuusars.exposure.ExposureClient.renderedExposures().getOrCreate(frame);
        float scale = 0.42f / Math.max(1, Math.max(image.width(), image.height()));
        float w = image.width() * scale, h = image.height() * scale;
        pose.pushPose();
        pose.translate(0.5, 1.008, 0.5);
        // GUI +Y points toward the user on the horizontal work surface; image normal faces up.
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-entity.getBlockState().getValue(LightroomBlock.FACING).toYRot()));
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90));
        quad(pose, buffers, -w/2-0.012f, -h/2-0.012f, w/2+0.012f, h/2+0.012f, 0xFFF4F0E6);
        pose.translate(0, 0, -0.001);
        io.github.mortuusars.exposure.ExposureClient.imageRenderer().render(image, pose, buffers,
                new io.github.mortuusars.exposure.client.render.image.RenderCoordinates(-w/2, -h/2, w, h),
                light, photographTint(entity));
        // A small printing lamp at the edge, never over the photograph; use Exposure's synced state.
        if (entity.isPrinting()) {
            float pulse = (float)(0.65 + 0.35 * Math.sin(dev.dyslexic.cinematic.client.Scene.clock() * 5));
            int color = 0xFF000000 | ((int)(255*pulse)<<16) | ((int)(192*pulse)<<8) | 64;
            quad(pose, buffers, -0.12f, h/2+0.022f, 0.12f, h/2+0.032f, color);
        }
        pose.popPose();
    }

    private static io.github.mortuusars.exposure.util.color.Color photographTint(LightroomBlockEntity entity) {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        // LightroomMenu.fromBuffer uses a separate SimpleContainerData on the client. Data packets
        // update that menu, NOT this block entity's progress fields. Match LightroomScreen's source.
        // Outside the menu, keep the persistent preview visible rather than using stale BE progress.
        if (player != null
                && player.containerMenu instanceof io.github.mortuusars.exposure.world.inventory.LightroomMenu menu
                && menu.getBlockEntity() == entity) {
            var data = menu.getData();
            int time = data.get(LightroomBlockEntity.CONTAINER_DATA_PRINT_TIME_ID);
            if (time > 0) {
                int progress = data.get(LightroomBlockEntity.CONTAINER_DATA_PROGRESS_ID);
                float alpha = (float) Motion.ease(progress / (double) time);
                // The paper and border stay opaque; only the image develops from blank to full colour.
                return io.github.mortuusars.exposure.util.color.Color.argbF(alpha,1,1,1);
            }
        }
        return io.github.mortuusars.exposure.util.color.Color.WHITE;
    }

    private static void quad(PoseStack pose, MultiBufferSource buffers, float x0, float y0, float x1, float y1, int color) {
        var vertex = buffers.getBuffer(net.minecraft.client.renderer.RenderType.debugQuads());
        var matrix = pose.last().pose();
        vertex.addVertex(matrix, x0, y1, 0).setColor(color);
        vertex.addVertex(matrix, x1, y1, 0).setColor(color);
        vertex.addVertex(matrix, x1, y0, 0).setColor(color);
        vertex.addVertex(matrix, x0, y0, 0).setColor(color);
    }
}
