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

    public static Vec3 pocket(Vec3 eye, Vec3 forward, boolean right) {
        return shoulder(eye, forward, right).add(forward.scale(0.04)).add(0, -0.46, 0);
    }
}
