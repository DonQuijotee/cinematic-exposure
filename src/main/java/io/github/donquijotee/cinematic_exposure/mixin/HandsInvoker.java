package io.github.donquijotee.cinematic_exposure.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.dyslexic.cinematic.client.Hands;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reuses the host's skin and sleeve renderer. No Scene fields are changed. */
@Mixin(value = Hands.class, remap = false)
public interface HandsInvoker {
    @Invoker("tap")
    static void cinematicExposure$tap() {
        throw new AssertionError("Mixin invoker was not applied");
    }

    /** Use the host's current operating hand, including its own cursor-side hysteresis. */
    @Accessor("leftWorks")
    static boolean cinematicExposure$leftWorks() {
        throw new AssertionError("Mixin accessor was not applied");
    }

    @Invoker("position")
    static Vec3 cinematicExposure$position(boolean right) {
        throw new AssertionError("Mixin invoker was not applied");
    }

    @Invoker("drawArm")
    static void cinematicExposure$drawArm(PoseStack pose, MultiBufferSource buffers, Vec3 camera,
            Vec3 hand, Vec3 shoulder, boolean right, int light, Vec3 forward) {
        throw new AssertionError("Mixin invoker was not applied");
    }
}
