package io.github.donquijotee.cinematic_exposure.mixin;

import io.github.donquijotee.cinematic_exposure.client.VisualSession;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow protected abstract void setPosition(Vec3 position);
    @Shadow protected abstract void setRotation(float yaw, float pitch, float roll);

    @Inject(method = "setup", at = @At("TAIL"))
    private void cinematicExposure$camera(BlockGetter level, Entity entity, boolean detached,
            boolean reversed, float partialTick, CallbackInfo ci) {
        Camera self = (Camera) (Object) this;
        var pose = VisualSession.camera(self.getPosition(), self.getYRot(), self.getXRot());
        if (pose != null) {
            setPosition(pose.pos());
            setRotation(pose.yaw(), pose.pitch(), 0);
        }
    }
}
