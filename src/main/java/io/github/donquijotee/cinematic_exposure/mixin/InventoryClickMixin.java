package io.github.donquijotee.cinematic_exposure.mixin;

import io.github.donquijotee.cinematic_exposure.client.MenuObservations;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observe completed vanilla prediction; never cancel, delay, resend or perform a click. */
@Mixin(MultiPlayerGameMode.class)
public abstract class InventoryClickMixin {
    @Inject(method = "handleInventoryMouseClick", at = @At("HEAD"))
    private void cinematicExposure$before(int containerId, int slot, int button, ClickType type,
            Player player, CallbackInfo ci) {
        MenuObservations.beforeClick(containerId);
    }
    @Inject(method = "handleInventoryMouseClick", at = @At("RETURN"))
    private void cinematicExposure$observe(int containerId, int slot, int button, ClickType type,
            Player player, CallbackInfo ci) {
        MenuObservations.afterClick(containerId);
    }
}
