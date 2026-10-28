package io.github.donquijotee.cinematic_exposure.mixin;

import io.github.donquijotee.cinematic_exposure.client.CameraSceneScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Optional EMI bridge: treat only this immersive screen as having no EMI screen base. */
@Pseudo
@Mixin(targets = "dev.emi.emi.screen.EmiScreenBase", remap = false)
public abstract class EmiCameraScreenMixin {
    // EMI's own null-screen branch returns an empty screen base. All normal screens retain their base.
    // Unlike exclusions or toggling EMI config, this also stops widget creation and recipe key handling.
    @ModifyVariable(method = "of(Lnet/minecraft/client/gui/screens/Screen;)Ldev/emi/emi/screen/EmiScreenBase;",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static Screen cinematicExposure$excludeCamera(Screen screen) {
        return screen instanceof CameraSceneScreen ? null : screen;
    }
}
