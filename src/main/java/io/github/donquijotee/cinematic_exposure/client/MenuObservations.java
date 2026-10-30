package io.github.donquijotee.cinematic_exposure.client;

import dev.dyslexic.cinematic.client.Scene;

/** Read-only boundary around original menu operations. No clicks or packets originate here. */
public final class MenuObservations {
    private MenuObservations() {}
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
