package io.github.donquijotee.cinematic_exposure.client;

import io.github.donquijotee.cinematic_exposure.client.CameraAssembly.Point;

/** Critically damped hand position with persistent velocity, independently checkable without a game. */
public final class HandSpring {
    private static final double MAX_SPEED = 3.2;
    private Point position, velocity = new Point(0,0,0);
    public HandSpring(Point initial) { position = initial; }
    public Point position() { return position; }
    public Point velocity() { return velocity; }
    public Point update(Point target, double dt) {
        dt = Math.max(0,dt);
        double omega = 26, decay = Math.exp(-omega*dt);
        Point delta = position.subtract(target);
        Point travel = velocity.add(delta.scale(omega)).scale(dt);
        Point next = target.add(delta.add(travel).scale(decay));
        Point movement = next.subtract(position);
        // A rapid slot change can move the target across the entire work area in one frame.
        // Cap travel as well as reach, retaining the existing direction/velocity for interruption.
        position = movement.length() > MAX_SPEED*dt ? position.add(movement.scale(MAX_SPEED*dt/movement.length())) : next;
        velocity = velocity.subtract(travel.scale(omega)).scale(decay);
        if (velocity.length() > MAX_SPEED) velocity = velocity.scale(MAX_SPEED/velocity.length());
        return position;
    }
    public Point limit(Point shoulder, double reach) {
        Point delta = position.subtract(shoulder);
        if (delta.length() > reach) {
            Point normal = delta.scale(1/delta.length());
            position = shoulder.add(normal.scale(reach));
            velocity = velocity.subtract(normal.scale(Math.max(0,velocity.dot(normal))));
        }
        return position;
    }
}
