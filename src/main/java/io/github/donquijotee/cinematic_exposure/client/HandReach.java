package io.github.donquijotee.cinematic_exposure.client;

import net.minecraft.world.phys.Vec3;

/** Shared visual reach bounds, including special poses that bypass the host's normal reach limit. */
public final class HandReach {
    public static final double MAX = 0.74;
    private HandReach() {}

    public static Vec3 shoulder(Vec3 eye, Vec3 forward, boolean right) {
        Vec3 side = new Vec3(-forward.z, 0, forward.x);
        return eye.add(side.scale(right ? 0.38 : -0.38)).add(0, -0.46, 0).add(forward.scale(0.08));
    }

    public static Vec3 limit(Vec3 hand, Vec3 shoulder) {
        Vec3 delta = hand.subtract(shoulder);
        return delta.lengthSqr() > MAX * MAX ? shoulder.add(delta.normalize().scale(MAX)) : hand;
    }

    /** Limit a free target along its safe anchor segment, rather than pulling it through the camera body. */
    public static Vec3 fromAnchor(Vec3 anchor, Vec3 target, Vec3 shoulder) {
        var limited = CameraAssembly.reachableFrom(point(anchor),point(target),point(shoulder),MAX);
        return new Vec3(limited.x(),limited.y(),limited.z());
    }
    private static CameraAssembly.Point point(Vec3 v) { return new CameraAssembly.Point(v.x,v.y,v.z); }
}
