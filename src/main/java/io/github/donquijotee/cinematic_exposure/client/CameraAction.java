package io.github.donquijotee.cinematic_exposure.client;

import net.minecraft.world.phys.Vec3;

/** A camera-relative path captured once per real attachment transfer. It never changes a menu. */
public final class CameraAction {
    private final Motion motion;
    private final Vec3 start, rest, carry, socket, outside;
    private final boolean alreadyHolding;
    private final CameraAssembly.FilmPath filmPath;

    public CameraAction(Motion motion, Vec3 start, Vec3 rest, Vec3 carry, Vec3 socket, Vec3 outward,
                        boolean alreadyHolding, Vec3 filmOutside) {
        this.motion = motion;
        this.start = start;
        this.rest = rest;
        this.carry = carry;
        this.socket = socket;
        this.alreadyHolding = alreadyHolding;
        this.outside = socket.add(outward.scale(motion.kind == Motion.Kind.FILTER ? 0.12 : 0.22));
        filmPath = motion.kind == Motion.Kind.FILM ? new CameraAssembly.FilmPath(point(start),point(rest),
                point(carry),point(socket),point(filmOutside),alreadyHolding) : null;
    }

    public Vec3 hand() {
        double t = motion.progress();
        if (filmPath != null) {
            var p = filmPath.hand(t,motion.inserting);
            return new Vec3(p.x(),p.y(),p.z());
        }
        if (motion.inserting) {
            if (alreadyHolding && t < 0.40) return move(start, outside, t, 0, 0.40);
            if (t < 0.18) return move(start, carry, t, 0, 0.18);
            if (t < 0.24) return carry;
            if (t < 0.40) return move(carry, outside, t, 0.24, 0.40);
            if (t < 0.58) return move(outside, socket, t, 0.40, 0.58);
            if (t < 0.80) return socket;
            if (motion.kind == Motion.Kind.FLASH && t < 0.90) return move(socket,outside,t,0.80,0.90);
            return move(motion.kind == Motion.Kind.FLASH ? outside : socket, rest, t,
                    motion.kind == Motion.Kind.FLASH ? 0.90 : 0.80, 1);
        }
        if (t < 0.18) return move(start, socket, t, 0, 0.18);
        if (t < 0.38) return socket; // grip and unscrew before any axial translation
        if (t < 0.62) return move(socket, outside, t, 0.38, 0.62);
        return move(outside, rest, t, 0.62, 1);
    }

    public boolean showsItem() {
        double t = motion.progress();
        return motion.inserting ? (alreadyHolding || t >= 0.21) && t < 0.80
                : t >= (filmPath != null ? 0.22 : 0.18);
    }

    public float twist() {
        if (motion.kind != Motion.Kind.LENS && motion.kind != Motion.Kind.FILTER) return 0;
        double t = motion.progress();
        return (float)(motion.inserting ? 90 * Motion.window(t, 0.58, 0.80) : -90 * Motion.window(t, 0.18, 0.38));
    }

    private static Vec3 move(Vec3 a, Vec3 b, double t, double from, double to) {
        return a.lerp(b, Motion.window(t, from, to));
    }
    private static CameraAssembly.Point point(Vec3 v) { return new CameraAssembly.Point(v.x,v.y,v.z); }
}
