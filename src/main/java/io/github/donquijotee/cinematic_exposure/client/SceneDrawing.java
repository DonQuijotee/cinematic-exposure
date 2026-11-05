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
        // Use the host Picking matrices captured earlier in this frame (AFTER_SKY).
        session.updatePointer();
        Vec3 rightHand = session.followTarget(true);
        Vec3 leftHand = session.followTarget(false);
        Motion motion = session.motion;
        CameraAction action = session.action();
        if (action != null) {
            // Keep the other hand supporting the body throughout a transfer.
            rightHand = session.rest(true); leftHand = session.rest(false);
            if (session.motionRight()) rightHand = session.actionTarget(action); else leftHand = session.actionTarget(action);
        }
        // Mouse following is limited to the operating side; the supporting grip stays on the body.
        Vec3 entrance = session.entranceOffset();
        leftHand = HandReach.limit(session.smooth(false,leftHand).add(entrance),leftShoulder);
        rightHand = HandReach.limit(session.smooth(true,rightHand).add(entrance),rightShoulder);
        if (motion != null) session.contact(session.motionRight() ? rightHand : leftHand);
        if (weight > 0.01) {
            arm(pose,buffers,eye,leftHand,leftShoulder,false,light,session);
            // Turning the entire arm about the socket also rotated the shoulder off the body.
            // Keep the arm anchored; only the held attachment turns during fastening.
            arm(pose,buffers,eye,rightHand,rightShoulder,true,light,session);
        }
        if (weight > 0.001) {
            item(pose, buffers, eye, session.cameraStack(), session.work.add(entrance), session.forward,
                    0.72f, CameraAssembly.TILT, 0, light);
        }
        if (action != null && action.showsItem() && weight > 0.99) {
            Vec3 held = session.motionRight() ? rightHand : leftHand;
            item(pose, buffers, eye, motion.item, held, session.forward,
                    0.22f * action.itemScale(), CameraAssembly.TILT, action.twist(), light);
        }
        if (motion == null && weight > 0.99
                && mc.screen instanceof CameraSceneScreen screen && screen.usesWorldCursor() && !screen.getMenu().getCarried().isEmpty()) {
            var carried = screen.getMenu().getCarried();
            item(pose,buffers,eye,carried,session.carriedRight() ? rightHand : leftHand,session.forward,
                    0.22f,CameraAssembly.TILT,0,light);
        }
        buffers.endBatch();
    }

    private static void arm(PoseStack pose, MultiBufferSource buffers, Vec3 eye, Vec3 hand,
                            Vec3 shoulder, boolean right, int light, VisualSession session) {
        Vec3 d = hand.subtract(shoulder).normalize();
        Vec3 base = new Vec3(0,1,0).subtract(d.scale(d.y));
        if (base.lengthSqr() < 1e-6) base = session.forward;
        base = base.normalize();
        var continuity = right ? session.rightArm : session.leftArm;
        var up = continuity.update(new CameraAssembly.Point(d.x,d.y,d.z),
                new CameraAssembly.Point(session.forward.x,session.forward.y,session.forward.z),session.frameStep());
        Vec3 stable = new Vec3(up.x(),up.y(),up.z());
        float roll = (float)Math.atan2(d.dot(base.cross(stable)),base.dot(stable));
        // Rotate about the shoulder-to-fist axis, leaving both endpoints exactly in place.
        // All mesh, sleeve and skin rendering still belongs to Cinematic's original renderer.
        pose.pushPose();
        Vec3 pivot = hand.subtract(eye);
        pose.translate(pivot.x,pivot.y,pivot.z);
        pose.mulPose(new org.joml.Quaternionf().rotationAxis(roll,(float)d.x,(float)d.y,(float)d.z));
        pose.translate(-pivot.x,-pivot.y,-pivot.z);
        HandsInvoker.cinematicExposure$drawArm(pose,buffers,eye,hand,shoulder,right,light,session.forward);
        pose.popPose();
    }

    private static void item(PoseStack pose, MultiBufferSource buffers, Vec3 eye, ItemStack stack,
            Vec3 at, Vec3 forward, float scale, float tilt, float twist, int light) {
        if (stack.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        pose.pushPose();
        pose.translate(at.x - eye.x, at.y - eye.y, at.z - eye.z);
        // Camera body and attachment models share the same diagonal assembly orientation.
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
