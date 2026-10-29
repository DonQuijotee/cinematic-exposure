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
        double weight = session.weight();
        Vec3 leftShoulder = HandReach.shoulder(eye, facing, false);
        Vec3 rightShoulder = HandReach.shoulder(eye, facing, true);
        Vec3 rightHand = session.rest(true);
        Vec3 leftHand = session.rest(false);
        Motion motion = session.motion;
        CameraAction action = session.action();
        if (action != null) {
            if (session.motionRight()) rightHand = action.hand(); else leftHand = action.hand();
        } else if (session.pickup.active()) {
            if (session.carriedRight()) rightHand = session.pickup.hand(); else leftHand = session.pickup.hand();
        } else if (session.pickup.showsItem()) {
            if (session.carriedRight()) rightHand = session.carry(true); else leftHand = session.carry(false);
        }
        // Hover only highlights slots. It must not switch arms or drag a hand across the camera.
        Vec3 entrance = session.entranceOffset();
        leftHand = HandReach.limit(session.smooth(false,leftHand).add(entrance),leftShoulder);
        rightHand = HandReach.limit(session.smooth(true,rightHand).add(entrance),rightShoulder);
        if (weight > 0.01) {
            HandsInvoker.cinematicExposure$drawArm(pose, buffers, eye, leftHand, leftShoulder, false, light, session.forward);
            // Turning the entire arm about the socket also rotated the shoulder off the body.
            // Keep the arm anchored; only the held attachment turns during fastening.
            HandsInvoker.cinematicExposure$drawArm(pose, buffers, eye, rightHand, rightShoulder, true, light, session.forward);
        }
        if (weight > 0.001) {
            item(pose, buffers, eye, session.cameraStack(), session.work.add(entrance), session.forward,
                    0.72f, CameraAssembly.TILT, 0, light);
        }
        if (action != null && action.showsItem() && weight > 0.99) {
            Vec3 held = session.motionRight() ? rightHand : leftHand;
            item(pose, buffers, eye, motion.item, held, session.forward,
                    0.22f, CameraAssembly.TILT, action.twist(), light);
        }
        if (motion == null && session.pickup.showsItem() && weight > 0.99
                && mc.screen instanceof CameraSceneScreen screen && !screen.getMenu().getCarried().isEmpty()) {
            var carried = screen.getMenu().getCarried();
            item(pose,buffers,eye,carried,session.carriedRight() ? rightHand : leftHand,session.forward,
                    0.22f,CameraAssembly.TILT,0,light);
        }
        buffers.endBatch();
    }

    private static void item(PoseStack pose, MultiBufferSource buffers, Vec3 eye, ItemStack stack,
            Vec3 at, Vec3 forward, float scale, float tilt, float twist, int light) {
        if (stack.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        pose.pushPose();
        pose.translate(at.x - eye.x, at.y - eye.y, at.z - eye.z);
        // Camera body and attachment models share the same side-on assembly orientation.
        float turn = stack.getItem() instanceof CameraItem ? 0 : 180;
        pose.mulPose(Axis.YP.rotationDegrees((float) Math.toDegrees(Math.atan2(-forward.x, forward.z)) * -1 + CameraAssembly.YAW));
        pose.mulPose(Axis.XP.rotationDegrees(tilt));
        pose.mulPose(Axis.ZP.rotationDegrees(twist));
        pose.mulPose(Axis.YP.rotationDegrees(turn));
        pose.scale(scale, scale, scale);
        // Exposure's camera body spans model Y=0..6. Center the actual body at the hand anchor,
        // bypassing its item-frame FIXED transform (which is intentionally offset and enlarged).
        if (stack.getItem() instanceof CameraItem) pose.translate(0, 5.0 / 16, 0);
        mc.getItemRenderer().renderStatic(stack, ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY,
                pose, buffers, mc.level, 0);
        pose.popPose();
    }
}
