package io.github.donquijotee.cinematic_exposure.client;

import net.minecraft.world.phys.Vec3;

/** A camera-relative path captured once per real attachment transfer. It never changes a menu. */
public final class CameraAction {
    private final Motion motion;
    private final Vec3 start, rest, carry, socket, outside;
    private final boolean alreadyHolding;

    public CameraAction(Motion motion, Vec3 start, Vec3 rest, Vec3 carry, Vec3 socket, Vec3 outward,
                        boolean alreadyHolding) {
        this.motion = motion;
        this.start = start;
        this.rest = rest;
        this.carry = carry;
        this.socket = socket;
        this.alreadyHolding = alreadyHolding;
        this.outside = socket.add(outward.scale(motion.kind == Motion.Kind.FILTER ? 0.12 : 0.22));
    }

    public Vec3 hand() {
        double t = motion.progress();
        if (motion.inserting) {
            if (alreadyHolding && t < 0.40) return move(start, outside, t, 0, 0.40);
            if (t < 0.18) return move(start, carry, t, 0, 0.18);
            if (t < 0.24) return carry;
            if (t < 0.40) return move(carry, outside, t, 0.24, 0.40);
            if (t < 0.58) return move(outside, socket, t, 0.40, 0.58);
            if (t < 0.80) return socket;
            return move(socket, rest, t, 0.80, 1);
        }
        if (t < 0.18) return move(start, socket, t, 0, 0.18);
        if (t < 0.38) return socket; // grip and unscrew before any axial translation
        if (t < 0.62) return move(socket, outside, t, 0.38, 0.62);
        return move(outside, rest, t, 0.62, 1);
    }

    public boolean showsItem() {
        double t = motion.progress();
        return motion.inserting ? (alreadyHolding || t >= 0.21) && t < 0.80 : t >= 0.18;
    }

    public float twist() {
        if (motion.kind != Motion.Kind.LENS && motion.kind != Motion.Kind.FILTER) return 0;
        double t = motion.progress();
        return (float)(motion.inserting ? 90 * Motion.window(t, 0.58, 0.80) : -90 * Motion.window(t, 0.18, 0.38));
    }

    private static Vec3 move(Vec3 a, Vec3 b, double t, double from, double to) {
        return a.lerp(b, Motion.window(t, from, to));
    }
}
