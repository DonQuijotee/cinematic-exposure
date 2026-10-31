package io.github.donquijotee.cinematic_exposure.client;

import dev.dyslexic.cinematic.client.Scene;

/** Observes original operations; the Lightroom placement lock only refuses new local input. */
public final class MenuObservations {
    private MenuObservations() {}
    public static boolean blocksClick(int containerId) {
        var station = Scene.station(LightroomStation.class);
        return station != null && station.blocksClick(containerId);
    }
    public static void beforeClick(int containerId) {
        VisualSession.beforeClick(containerId);
        var station = Scene.station(LightroomStation.class);
        if (station != null) station.beforeClick(containerId);
    }
    public static void afterClick(int containerId) {
        VisualSession.afterClick(containerId);
        var station = Scene.station(LightroomStation.class);
        if (station != null) station.afterClick(containerId);
    }
    public static void synchronize(int containerId) {
        VisualSession.resynchronize(containerId);
        var station = Scene.station(LightroomStation.class);
        if (station != null) station.synchronize(containerId);
    }
}
