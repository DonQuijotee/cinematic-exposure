package io.github.donquijotee.cinematic_exposure.client;

/** Pure interaction geometry: independent of menus, renderers and game state. */
public final class InteractionMotion {
    private InteractionMotion() {}

    /** Identity in the centre, with continuous slope and diminishing motion near either boundary. */
    public static double softBound(double value, double min, double max, double margin) {
        double edge = Math.min(margin, (max-min)/2);
        if (value < min+edge) return min+edge-edge*Math.tanh((min+edge-value)/edge);
        if (value > max-edge) return max-edge+edge*Math.tanh((value-max+edge)/edge);
        return value;
    }

    /** Hysteresis: hold the current hand around the middle, switch only after a deliberate crossing. */
    public static boolean chooseRight(boolean current, double lateral) {
        return current ? lateral >= -0.14 : lateral > 0.14;
    }

    public static double attraction(double distance, double radius) {
        return CameraAssembly.ease(1-distance/radius);
    }
}
