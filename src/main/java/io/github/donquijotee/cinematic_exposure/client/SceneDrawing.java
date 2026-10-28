package io.github.donquijotee.cinematic_exposure.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.donquijotee.cinematic_exposure.mixin.HandsInvoker;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import io.github.mortuusars.exposure.world.item.camera.CameraItem;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Quaternionf;

/** Uses original Exposure item models and the host's existing first-person arm renderer. */
public final class SceneDrawing {
    private SceneDrawing() {}

    public static void render(RenderLevelStageEvent event) {
        VisualSession session = VisualSession.get();
        Minecraft mc = Minecraft.getInstance();
        if (session == null || mc.level == null || mc.player == null || mc.player.isInvisible()) return;
        Camera camera = event.getCamera();
        Vec3 eye = camera.getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        int light = LevelRenderer.getLightColor(mc.level, session.lightPos);
        Vec3 facing = VisualSession.direction(camera.getYRot());
        Vec3 side = new Vec3(-facing.z, 0, facing.x);
        double weight = session.weight();
        Vec3 leftShoulder = eye.add(side.scale(-0.34)).add(0, -0.43, 0).add(facing.scale(0.06));
        Vec3 rightShoulder = eye.add(side.scale(0.34)).add(0, -0.43, 0).add(facing.scale(0.06));
        Vec3 rightHand = session.rest(true);
        Vec3 leftHand = session.rest(false);
        Motion motion = session.motion;
        CameraAction action = session.action();
        if (action != null) rightHand = action.hand();
        if (motion == null && mc.screen instanceof CameraSceneScreen screen) {
            int slot = screen.hoveredAttachment();
            if (slot >= 0 && slot < screen.getMenu().slots.size()-36) rightHand = session.socket(screen.kind(slot));
        }
        Vec3 entrance = session.entranceOffset();
        leftHand = leftHand.add(entrance);
        rightHand = rightHand.add(entrance);
        // The camera and supporting hand must stay locked together throughout the lift.
        rightHand = session.smooth(true, rightHand);
        if (weight > 0.01) {
            HandsInvoker.cinematicExposure$drawArm(pose, buffers, eye, leftHand, leftShoulder, false, light, session.forward);
            pose.pushPose();
            if (action != null && action.showsItem() && action.twist() != 0) {
                Vec3 axis = session.forward.scale(-1);
                Vec3 pivot = rightHand.subtract(eye);
                pose.translate(pivot.x, pivot.y, pivot.z);
                pose.mulPose(new Quaternionf().rotationAxis((float) Math.toRadians(action.twist()),
                        (float) axis.x, (float) axis.y, (float) axis.z));
                pose.translate(-pivot.x, -pivot.y, -pivot.z);
            }
            HandsInvoker.cinematicExposure$drawArm(pose, buffers, eye, rightHand, rightShoulder, true, light, session.forward);
            pose.popPose();
        }
        if (weight > 0.001) {
            item(pose, buffers, eye, session.cameraStack(), session.work.add(entrance), session.forward,
                    0.72f, 0, 0, light);
        }
        if (action != null && action.showsItem() && weight > 0.99) {
            Vec3 held = rightHand;
            item(pose, buffers, eye, motion.item, held, session.forward,
                    0.22f, motion.kind == Motion.Kind.FILM ? 10 : 0, action.twist(), light);
        }
        if (motion == null && mc.screen instanceof CameraSceneScreen screen && !screen.getMenu().getCarried().isEmpty()) {
            item(pose,buffers,eye,screen.getMenu().getCarried(),rightHand,session.forward,0.22f,0,0,light);
        }
        buffers.endBatch();
    }

    private static void item(PoseStack pose, MultiBufferSource buffers, Vec3 eye, ItemStack stack,
            Vec3 at, Vec3 forward, float scale, float tilt, float twist, int light) {
        if (stack.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        pose.pushPose();
        pose.translate(at.x - eye.x, at.y - eye.y, at.z - eye.z);
        // Face the camera's lens toward the viewer, so the complete mounting action stays visible.
        float turn = stack.getItem() instanceof CameraItem ? 0 : 180;
        pose.mulPose(Axis.YP.rotationDegrees((float) Math.toDegrees(Math.atan2(-forward.x, forward.z)) * -1 + turn));
        pose.mulPose(Axis.XP.rotationDegrees(tilt));
        pose.mulPose(Axis.ZP.rotationDegrees(twist));
        pose.scale(scale, scale, scale);
        // Exposure's camera body spans model Y=0..6. Center the actual body at the hand anchor,
        // bypassing its item-frame FIXED transform (which is intentionally offset and enlarged).
        if (stack.getItem() instanceof CameraItem) pose.translate(0, 5.0 / 16, 0);
        mc.getItemRenderer().renderStatic(stack, ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY,
                pose, buffers, mc.level, 0);
        pose.popPose();
    }
}
