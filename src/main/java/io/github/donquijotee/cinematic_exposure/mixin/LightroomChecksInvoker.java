package io.github.donquijotee.cinematic_exposure.mixin;

import io.github.mortuusars.exposure.world.block.entity.LightroomBlockEntity;
import io.github.mortuusars.exposure.world.camera.frame.Frame;
import io.github.mortuusars.exposure.world.lightroom.PrintingProcess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Read-only access to the exact Exposure eligibility checks used by canPrint. */
@Mixin(value = LightroomBlockEntity.class, remap = false)
public interface LightroomChecksInvoker {
    @Invoker("getPrintingProcess") PrintingProcess cinematicExposure$process(Frame frame);
    @Invoker("isPaperValidForPrint") boolean cinematicExposure$paper(Frame frame, PrintingProcess process);
    @Invoker("hasDyesForPrint") boolean cinematicExposure$dyes(Frame frame, PrintingProcess process);
}
