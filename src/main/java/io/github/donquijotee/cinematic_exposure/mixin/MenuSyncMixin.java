package io.github.donquijotee.cinematic_exposure.mixin;

import io.github.donquijotee.cinematic_exposure.client.MenuObservations;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Read only, after vanilla's existing packet handlers have finished applying synchronized state. */
@Mixin(ClientPacketListener.class)
public abstract class MenuSyncMixin {
    @Inject(method = "handleContainerContent", at = @At("RETURN"))
    private void cinematicExposure$contents(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
        MenuObservations.synchronize(packet.getContainerId());
    }

    @Inject(method = "handleContainerSetSlot", at = @At("RETURN"))
    private void cinematicExposure$slot(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
        MenuObservations.synchronize(packet.getContainerId());
    }
}
