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
        if (motion != null) {
            Vec3 pickup = session.local(0.36, -0.07, -0.24);
            rightHand = motion.hand(rightHand, pickup, session.socket(motion.kind));
            if (!session.camera && motion.kind == Motion.Kind.PRINT) {
                // The left hand steadies the paper while the right hand presses the enlarger control.
                double hold = Motion.window(motion.progress(), 0, 0.3)
                        * (1 - Motion.window(motion.progress(), 0.7, 1));
                leftHand = leftHand.lerp(session.local(-0.12, 0.04, 0.03), hold);
            }
        }
        leftHand = leftShoulder.add(facing.scale(0.08)).lerp(leftHand, weight);
        rightHand = rightShoulder.add(facing.scale(0.08)).lerp(rightHand, weight);
        leftHand = session.smooth(false, leftHand);
        rightHand = session.smooth(true, rightHand);
        if (weight > 0.01) {
            HandsInvoker.cinematicExposure$drawArm(pose, buffers, eye, leftHand, leftShoulder, false, light, session.forward);
            pose.pushPose();
            if (motion != null && motion.twist() != 0) {
                Vec3 axis = rightHand.subtract(rightShoulder).normalize();
                Vec3 pivot = rightHand.subtract(eye);
                pose.translate(pivot.x, pivot.y, pivot.z);
                pose.mulPose(new Quaternionf().rotationAxis((float) Math.toRadians(motion.twist()),
                        (float) axis.x, (float) axis.y, (float) axis.z));
                pose.translate(-pivot.x, -pivot.y, -pivot.z);
            }
            HandsInvoker.cinematicExposure$drawArm(pose, buffers, eye, rightHand, rightShoulder, true, light, session.forward);
            pose.popPose();
        }
        if (session.camera && weight > 0.01) {
            Vec3 location = session.work.add(0, -0.28 * (1 - weight), 0);
            item(pose, buffers, eye, session.cameraStack(), location, session.forward,
                    (float) (0.72 * weight), 0, 0, light);
        } else if (!session.camera && weight > 0.01) {
            if (motion == null || motion.kind != Motion.Kind.FILM || !motion.inserting || motion.progress() >= 0.78) {
                item(pose, buffers, eye, session.film(), session.socket(Motion.Kind.FILM),
                        session.forward, 0.18f, 0, 0, light);
            }
            item(pose, buffers, eye, session.photo(), session.socket(Motion.Kind.PHOTO),
                    session.forward, 0.24f, 90, 0, light);
        }
        if (motion != null && motion.showsItem() && weight > 0.01) {
            Vec3 held = rightHand.add(session.forward.scale(0.035)).add(0, 0.015, 0);
            float tilt = motion.kind == Motion.Kind.PHOTO ? 75 : motion.kind == Motion.Kind.FILM ? 10 : 0;
            item(pose, buffers, eye, motion.item, held, session.forward,
                    (float) (0.22 * weight), tilt, motion.twist(), light);
        }
        buffers.endBatch();
    }

    private static void item(PoseStack pose, MultiBufferSource buffers, Vec3 eye, ItemStack stack,
            Vec3 at, Vec3 forward, float scale, float tilt, float twist, int light) {
        if (stack.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        pose.pushPose();
        pose.translate(at.x - eye.x, at.y - eye.y, at.z - eye.z);
        pose.mulPose(Axis.YP.rotationDegrees((float) Math.toDegrees(Math.atan2(-forward.x, forward.z)) + 180));
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
