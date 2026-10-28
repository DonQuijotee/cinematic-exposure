package io.github.donquijotee.cinematic_exposure.mixin;

import dev.dyslexic.cinematic.api.client.AddonScenes;
import io.github.donquijotee.cinematic_exposure.ClientConfig;
import io.github.mortuusars.exposure.world.block.LightroomBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=AddonScenes.class,remap=false)
public abstract class AddonAvailabilityMixin {
    @Inject(method="available",at=@At("HEAD"),cancellable=true)
    private static void cinematicExposure$enabled(BlockState state,CallbackInfoReturnable<Boolean> cir) {
        if(state.getBlock() instanceof LightroomBlock && !ClientConfig.LIGHTROOM.get()) cir.setReturnValue(false);
    }
}
