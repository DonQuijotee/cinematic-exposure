package io.github.donquijotee.cinematic_exposure.client;

import io.github.donquijotee.cinematic_exposure.CinematicExposure;
import io.github.donquijotee.cinematic_exposure.ClientConfig;
import io.github.donquijotee.cinematic_exposure.mixin.ContainerScreenAccessor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

@EventBusSubscriber(modid = CinematicExposure.MOD_ID, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void frame(RenderFrameEvent.Pre event) { VisualSession.frame(); }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) { VisualSession.observe(); }

    @SubscribeEvent
    public static void world(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) SceneDrawing.render(event);
    }

    @SubscribeEvent
    public static void hand(RenderHandEvent event) {
        if (VisualSession.get() != null && VisualSession.get().weight() > 0.01) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void fov(ViewportEvent.ComputeFov event) {
        if (VisualSession.get() != null && event.usedConfiguredFov()) {
            double weight = VisualSession.get().weight();
            event.setFOV(event.getFOV() + (66 - event.getFOV()) * weight);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void init(ScreenEvent.Init.Post event) {
        var screen = event.getScreen();
        if (!ClientConfig.DOCK_GUI.get() || !VisualSession.supports(screen)
                || screen.width < 440 || !(screen instanceof AbstractContainerScreen<?>)) return;
        ContainerScreenAccessor access = (ContainerScreenAccessor) screen;
        int target = screen.width - access.cinematicExposure$width() - 22;
        int delta = target - access.cinematicExposure$left();
        access.cinematicExposure$left(target);
        // Subclass widgets have already been created using the original origin. Move them by the same amount.
        for (var child : screen.children()) {
            if (child instanceof AbstractWidget widget) widget.setX(widget.getX() + delta);
        }
    }
}
