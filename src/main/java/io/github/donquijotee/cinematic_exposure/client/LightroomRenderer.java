package io.github.donquijotee.cinematic_exposure.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.dyslexic.cinematic.client.ItemPlacer;
import io.github.donquijotee.cinematic_exposure.ClientConfig;
import io.github.mortuusars.exposure.ExposureClient;
import io.github.mortuusars.exposure.client.image.modifier.ImageEffect;
import io.github.mortuusars.exposure.client.render.image.RenderCoordinates;
import io.github.mortuusars.exposure.util.color.Color;
import io.github.mortuusars.exposure.world.camera.frame.Frame;
import io.github.mortuusars.exposure.world.inventory.LightroomMenu;
import io.github.mortuusars.exposure.world.item.ChromaticSheetItem;
import io.github.mortuusars.exposure.world.item.PhotographItem;
import io.github.mortuusars.exposure.world.item.StackedPhotographsItem;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
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
            ItemStack actual = entity.getItem(slot);
            ItemStack displayed = station == null ? actual : station.displayedSlot(pos,slot,actual);
            ItemPlacer.renderOne(pose, buffers, origin, displayed,
                    LightroomLayout.point(pos, facing, slot), facing, pos.asLong() + slot, 0.72f, 0, light);
        }
        renderPhotograph(entity, pose, buffers, light);
    }

    private void renderPhotograph(LightroomBlockEntity entity, PoseStack pose, MultiBufferSource buffers, int light) {
        var menu = matchingMenu(entity);
        var station = dev.dyslexic.cinematic.client.Scene.station(LightroomStation.class);
        boolean placingFilm = station != null && station.hidesFilmPreview(entity.getBlockPos());
        // Selection, like progress, is synchronized into the client's separate menu data.
        // The real menu already contains a newly inserted film during its placement animation.
        // Reveal that film's preview only when the same handoff has made it visible on the table.
        var frame = placingFilm ? null : menu != null ? menu.getFrameByIndex(menu.getSelectedFrame()) : entity.getSelectedFrame();
        boolean filmPreview = frame != null;
        // With no film, show the real top output photograph, if any. Never mutate the stored stack.
        if (frame == null) {
            var output = entity.getItem(6);
            if (output.getItem() instanceof StackedPhotographsItem stack) {
                var photos = stack.getPhotographs(output);
                if (!photos.isEmpty()) output = photos.getItemUnsafe(photos.size() - 1);
            }
            if (output.getItem() instanceof PhotographItem photo)
                frame = photo.getFrame(output);
        }
        if (frame == null) return;
        var image = ExposureClient.renderedExposures().getOrCreate(frame);
        float scale = 0.42f / Math.max(1, Math.max(image.width(), image.height()));
        float w = image.width() * scale, h = image.height() * scale;
        pose.pushPose();
        pose.translate(0.5, 1.008, 0.5);
        // GUI +Y points toward the user on the horizontal work surface; image normal faces up.
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-entity.getBlockState().getValue(LightroomBlock.FACING).toYRot()));
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90));
        quad(pose, buffers, -w/2-0.012f, -h/2-0.012f, w/2+0.012f, h/2+0.012f, 0xFFF4F0E6);
        pose.translate(0, 0, -0.001);
        float developed = filmPreview ? development(entity, menu, frame) : 1;
        var coords = new RenderCoordinates(-w/2, -h/2, w, h);
        // The original negative effect is a read-only wrapper with a stable variant identifier.
        // Exposure owns the two texture caches; progress never creates new texture variants.
        if (developed < 1) {
            ExposureClient.imageRenderer().render(image.modifyWith(ImageEffect.NEGATIVE), pose, buffers,
                    coords, light, Color.WHITE);
        }
        if (developed > 0) {
            // Positive lies just above the opaque negative, avoiding coplanar depth flicker.
            pose.translate(0, 0, -0.0005);
            ExposureClient.imageRenderer().render(image, pose, buffers, coords, light,
                    Color.argbF(developed, 1, 1, 1));
        }
        pose.popPose();
    }

    private static LightroomMenu matchingMenu(LightroomBlockEntity entity) {
        var player = Minecraft.getInstance().player;
        return player != null && player.containerMenu instanceof LightroomMenu menu
                && menu.getBlockEntity() == entity ? menu : null;
    }

    private static float development(LightroomBlockEntity entity, LightroomMenu menu, Frame frame) {
        // Match LightroomScreen: the client's menu data, NOT the BE's stale progress fields.
        if (menu != null) {
            var data = menu.getData();
            int time = data.get(LightroomBlockEntity.CONTAINER_DATA_PRINT_TIME_ID);
            if (time > 0) {
                int progress = data.get(LightroomBlockEntity.CONTAINER_DATA_PROGRESS_ID);
                return (float) Motion.ease(progress / (double) time);
            }
        }
        // Keep completed prints positive based on real output data, including after leaving/reloading.
        // Cancellation without a matching output returns to negative. No local completion flag or timer.
        return containsFrame(entity.getItem(6), frame) ? 1 : 0;
    }

    private static boolean containsFrame(ItemStack output, Frame frame) {
        if (output.getItem() instanceof PhotographItem photo) {
            return photo.getFrame(output).identifier().equals(frame.identifier());
        }
        if (output.getItem() instanceof StackedPhotographsItem stack) {
            var photos = stack.getPhotographs(output);
            for (int i = 0; i < photos.size(); i++) {
                var photograph = photos.getItemUnsafe(i);
                if (photograph.getItem() instanceof PhotographItem photo
                        && photo.getFrame(photograph).identifier().equals(frame.identifier())) return true;
            }
        }
        if (output.getItem() instanceof ChromaticSheetItem sheet) {
            return sheet.getLayers(output).stream().anyMatch(layer -> layer.identifier().equals(frame.identifier()));
        }
        return false;
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
