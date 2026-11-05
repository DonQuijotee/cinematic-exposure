package io.github.donquijotee.cinematic_exposure.client;

import net.minecraft.world.phys.Vec3;

/** One admitted placement, observed from Exposure's real click. Removal has no separate timeline. */
public final class CameraAction {
    private final Motion motion;
    private final CameraAssembly.Placement path;

    public CameraAction(Motion motion, CameraAssembly.Placement path) {
        this.motion = motion;
        this.path = path;
    }

    public Vec3 hand() {
        var p = path.hand(motion.progress());
        return new Vec3(p.x(),p.y(),p.z());
    }
    public boolean showsItem() { return !motion.contacted() && itemScale() > .001; }
    public float itemScale() { return (float)path.itemScale(motion.progress()); }
    public float twist() { return (float)path.twist(motion.progress()); }
}
