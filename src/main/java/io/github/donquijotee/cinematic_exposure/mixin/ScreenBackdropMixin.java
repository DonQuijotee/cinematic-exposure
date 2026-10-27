package io.github.donquijotee.cinematic_exposure.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.donquijotee.cinematic_exposure.client.VisualSession;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ScreenBackdropMixin {
    @Inject(method = "renderTransparentBackground", at = @At("HEAD"), cancellable = true)
    private void cinematicExposure$clearBackdrop(GuiGraphics graphics, CallbackInfo ci) {
        if (VisualSession.get() != null && VisualSession.supports((Screen) (Object) this)) {
            // The original container texture, slots, widgets and tooltips still render normally.
            // Only omit the full-screen dark gradient over the animated work area.
            RenderSystem.disableDepthTest();
            ci.cancel();
        }
    }
}
