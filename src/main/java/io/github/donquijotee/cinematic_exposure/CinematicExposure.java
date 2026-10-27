package io.github.donquijotee.cinematic_exposure;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(CinematicExposure.MOD_ID)
public final class CinematicExposure {
    public static final String MOD_ID = "cinematic_exposure";

    public CinematicExposure(ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
    }
}
