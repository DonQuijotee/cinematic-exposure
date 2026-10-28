package io.github.donquijotee.cinematic_exposure;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(CinematicExposure.MOD_ID)
public final class CinematicExposure {
    public static final String MOD_ID = "cinematic_exposure";

    public CinematicExposure(ModContainer container, net.neoforged.bus.api.IEventBus bus) {
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        bus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) -> event.enqueueWork(() ->
            dev.dyslexic.cinematic.api.AddonStations.register(new dev.dyslexic.cinematic.api.AddonStations.Definition(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MOD_ID, "lightroom"),
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("exposure", "lightroom"),
                io.github.mortuusars.exposure.world.inventory.LightroomMenu.class,
                state -> java.util.List.of(state.getValue(io.github.mortuusars.exposure.world.block.LightroomBlock.FACING))))));
    }
}
