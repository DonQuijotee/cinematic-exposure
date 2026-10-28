package io.github.donquijotee.cinematic_exposure.client;

import io.github.donquijotee.cinematic_exposure.CinematicExposure;
import io.github.donquijotee.cinematic_exposure.ClientConfig;
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

    /** Match the host's immersive HUD policy without changing hideGui or any saved user setting. */
    @SubscribeEvent
    public static void guiLayer(net.neoforged.neoforge.client.event.RenderGuiLayerEvent.Pre event) {
        var screen = net.minecraft.client.Minecraft.getInstance().screen;
        boolean camera = screen instanceof CameraSceneScreen
                || screen == null && VisualSession.get() != null && VisualSession.get().weight() > 0.01;
        if (camera && !CAMERA_HUD_KEEP.contains(event.getName())) event.setCanceled(true);
    }

    private static final java.util.Set<net.minecraft.resources.ResourceLocation> CAMERA_HUD_KEEP = java.util.Set.of(
            net.neoforged.neoforge.client.gui.VanillaGuiLayers.CHAT,
            net.neoforged.neoforge.client.gui.VanillaGuiLayers.SUBTITLE_OVERLAY,
            net.neoforged.neoforge.client.gui.VanillaGuiLayers.DEBUG_OVERLAY,
            net.neoforged.neoforge.client.gui.VanillaGuiLayers.SAVING_INDICATOR,
            net.neoforged.neoforge.client.gui.VanillaGuiLayers.TAB_LIST);

    @SubscribeEvent
    public static void fov(ViewportEvent.ComputeFov event) {
        if (VisualSession.get() != null && event.usedConfiguredFov()) {
            double weight = VisualSession.get().weight();
            event.setFOV(event.getFOV() + (66 - event.getFOV()) * weight);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void opening(ScreenEvent.Opening event) {
        if (ClientConfig.CAMERA.get() && net.minecraft.client.Minecraft.getInstance().options.getCameraType().isFirstPerson()
                && event.getNewScreen() instanceof
                io.github.mortuusars.exposure.client.gui.screen.camera.CameraAttachmentsScreen screen
                && screen.getMenu() instanceof io.github.mortuusars.exposure.world.inventory.CameraInHandAttachmentsMenu menu) {
            event.setNewScreen(new CameraSceneScreen(menu));
        }
    }
}
