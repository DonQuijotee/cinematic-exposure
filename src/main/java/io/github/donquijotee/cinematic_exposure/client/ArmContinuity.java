package io.github.donquijotee.cinematic_exposure.client;

import io.github.donquijotee.cinematic_exposure.client.CameraAssembly.Point;

/** Continuous palm roll through vertical arm poses. Keeps the host's skin, mesh and shoulder anchor. */
public final class ArmContinuity {
    private Point up;
    public Point update(Point direction, Point forward, double dt) {
        Point d = unit(direction);
        Point preferred = project(new Point(0,1,0),d);
        Point previous = up == null ? preferred.length() > .08 ? preferred : project(forward,d) : project(up,d);
        if (previous.length() < 1e-6) previous = project(new Point(1,0,0),d);
        if (previous.length() < 1e-6) previous = project(new Point(0,0,1),d);
        previous = unit(previous);
        if (preferred.length() > .08) {
            preferred = unit(preferred);
            // The two equivalent roll directions must not flip 180 degrees at the pole.
            if (preferred.dot(previous) < 0) preferred = preferred.scale(-1);
            double angle = Math.atan2(d.dot(previous.cross(preferred)),previous.dot(preferred));
            double step = Math.clamp(angle,-6*Math.max(0,dt),6*Math.max(0,dt));
            previous = previous.scale(Math.cos(step)).add(d.cross(previous).scale(Math.sin(step)));
        }
        up = unit(project(previous,d));
        return up;
    }
    private static Point project(Point p, Point normal) { return p.subtract(normal.scale(p.dot(normal))); }
    private static Point unit(Point p) { return p.length() > 1e-9 ? p.scale(1/p.length()) : new Point(0,0,1); }
}
