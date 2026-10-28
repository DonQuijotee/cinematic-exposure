package io.github.donquijotee.cinematic_exposure.mixin;

import dev.dyslexic.cinematic.client.SceneScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Scoped to the private drawer screen owned by CameraSceneScreen. No global Scene fields are touched. */
@Mixin(value = SceneScreen.class, remap = false)
public interface SceneScreenAccess {
    @Accessor("expand") void cinematicExposure$expand(double value);
    @Accessor("scale") void cinematicExposure$scale(float value);
    @Accessor("panelX") void cinematicExposure$x(int value);
    @Accessor("panelY") void cinematicExposure$y(int value);
    @Accessor("panelW") void cinematicExposure$width(int value);
    @Accessor("panelH") void cinematicExposure$height(int value);
    @Invoker("drawPanel") void cinematicExposure$draw(GuiGraphics graphics, LocalPlayer player, int hovered, float alpha);
    @Invoker("slotAt") int cinematicExposure$slotAt(double x, double y);
    @Invoker("overPanel") boolean cinematicExposure$over(double x, double y);
}
