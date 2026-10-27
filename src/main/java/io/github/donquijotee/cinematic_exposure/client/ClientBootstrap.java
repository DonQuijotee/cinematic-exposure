package io.github.donquijotee.cinematic_exposure.client;

import io.github.donquijotee.cinematic_exposure.CinematicExposure;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = CinematicExposure.MOD_ID, dist = Dist.CLIENT)
public final class ClientBootstrap {
    public ClientBootstrap(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
