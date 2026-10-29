package io.github.donquijotee.cinematic_exposure.mixin;

import io.github.donquijotee.cinematic_exposure.client.CameraSceneScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hide only the duplicate cursor sprite in the immersive work area, never slots or drag previews. */
@Mixin(AbstractContainerScreen.class)
public abstract class CameraCarriedItemMixin {
    @Inject(method = "renderFloatingItem", at = @At("HEAD"), cancellable = true)
    private void cinematicExposure$worldItem(GuiGraphics graphics, ItemStack stack, int x, int y,
                                            String text, CallbackInfo ci) {
        if ((Object)this instanceof CameraSceneScreen screen && screen.usesWorldCursor()
                && ItemStack.isSameItemSameComponents(stack,screen.getMenu().getCarried())) ci.cancel();
    }
}
