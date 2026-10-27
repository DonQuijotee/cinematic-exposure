package io.github.donquijotee.cinematic_exposure;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue CAMERA;
    public static final ModConfigSpec.BooleanValue LIGHTROOM;
    public static final ModConfigSpec.DoubleValue SPEED;
    public static final ModConfigSpec.BooleanValue DOCK_GUI;

    static {
        var builder = new ModConfigSpec.Builder();
        builder.push("animations");
        CAMERA = builder.comment("Enable handheld camera attachment animations.")
                .translation("cinematic_exposure.configuration.camera").define("camera", true);
        LIGHTROOM = builder.comment("Enable Lightroom animations.")
                .translation("cinematic_exposure.configuration.lightroom").define("lightroom", true);
        SPEED = builder.comment("Animation playback speed, including camera transitions.")
                .translation("cinematic_exposure.configuration.speed").defineInRange("speed", 1.0, 0.25, 3.0);
        DOCK_GUI = builder.comment("Move the original window to the right when scaled width is at least 440.")
                .translation("cinematic_exposure.configuration.dockGui").define("dockGui", true);
        builder.pop();
        SPEC = builder.build();
    }
    private ClientConfig() {}
}
