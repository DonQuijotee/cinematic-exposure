package io.github.donquijotee.cinematic_exposure.client;

import io.github.donquijotee.cinematic_exposure.client.CameraAssembly.Point;

/** One visual prop crosses between two real, smoothed hands. Never changes the carried stack. */
public final class HandExchange {
    private static final double DURATION = 0.70;
    private boolean right = true, destination = true;
    private double elapsed = DURATION;
    private Point fromRight = new Point(0,0,0), fromLeft = new Point(0,0,0), meetRight = new Point(0,0,0), meetLeft = new Point(0,0,0);

    public boolean right() { return active() ? (progress() < 0.5 ? right : destination) : right; }
    public boolean active() { return elapsed < DURATION; }
    public boolean destinationRight() { return destination; }
    private double progress() { return elapsed/DURATION; }
    public void reset(boolean owner) { right = destination = owner; elapsed = DURATION; }
    public void begin(boolean owner, Point rightHand, Point leftHand, Point meeting, Point rightAxis) {
        if (active() || owner == right) return;
        destination = owner;
        fromRight = rightHand; fromLeft = leftHand;
        meetRight = meeting.add(rightAxis.scale(0.075));
        meetLeft = meeting.add(rightAxis.scale(-0.075));
        elapsed = 0;
    }
    public void advance(double dt) {
        if (!active()) return;
        elapsed = Math.min(DURATION, elapsed+Math.max(0,dt));
        if (!active()) right = destination;
    }
    public Point[] targets(Point rightDestination, Point leftDestination) {
        double t = progress();
        if (t < 0.38) {
            double k = CameraAssembly.ease(t/0.38);
            return new Point[]{fromRight.lerp(meetRight,k),fromLeft.lerp(meetLeft,k)};
        }
        double k = CameraAssembly.ease((t-0.66)/0.34);
        return new Point[]{meetRight.lerp(rightDestination,k),meetLeft.lerp(leftDestination,k)};
    }
    public Point itemPoint(Point actualRight, Point actualLeft) {
        if (!active()) return right ? actualRight : actualLeft;
        double k = CameraAssembly.ease((progress()-0.42)/0.20);
        return (right ? actualRight : actualLeft).lerp(destination ? actualRight : actualLeft,k);
    }
}
