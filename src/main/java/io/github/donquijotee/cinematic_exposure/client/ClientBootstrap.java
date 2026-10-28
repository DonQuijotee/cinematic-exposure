package io.github.donquijotee.cinematic_exposure.client;

import io.github.donquijotee.cinematic_exposure.CinematicExposure;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = CinematicExposure.MOD_ID, dist = Dist.CLIENT)
public final class ClientBootstrap {
    public ClientBootstrap(ModContainer container, net.neoforged.bus.api.IEventBus bus) {
        bus.addListener((net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) -> event.enqueueWork(() ->
            dev.dyslexic.cinematic.api.client.AddonScenes.register(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(CinematicExposure.MOD_ID, "lightroom"),
                (pos, state, player, side) -> new LightroomStation(pos, side))));
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
