package io.github.donquijotee.cinematic_exposure.mixin;

import dev.dyslexic.cinematic.client.SceneScreen;
import io.github.donquijotee.cinematic_exposure.client.CinematicDrawer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Original workstation screens are unaffected. Camera draws stacks through vanilla quick-craft rendering. */
@Mixin(value = SceneScreen.class, remap = false)
public abstract class CameraDrawerMixin {
    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void cinematicExposure$init(CallbackInfo ci) {
        if (CinematicDrawer.owns(this)) ci.cancel();
    }

    @Redirect(method = "drawSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;renderItem(Lnet/minecraft/world/item/ItemStack;II)V"))
    private void cinematicExposure$item(GuiGraphics graphics, ItemStack stack, int x, int y) {
        if (!CinematicDrawer.owns(this)) graphics.renderItem(stack, x, y);
    }

    @Redirect(method = "drawSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;II)V"))
    private void cinematicExposure$count(GuiGraphics graphics, Font font, ItemStack stack, int x, int y) {
        if (!CinematicDrawer.owns(this)) graphics.renderItemDecorations(font, stack, x, y);
    }
}
