package io.github.donquijotee.cinematic_exposure.client;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/** One block-relative layout for the renderer, picking and hand animation. */
public final class LightroomLayout {
    private LightroomLayout() {}
    public static Vec3 top(BlockPos pos) { return Vec3.atBottomCenterOf(pos).add(0, 1.006, 0); }
    public static Vec3 right(Direction facing) {
        Vec3 side = Vec3.atLowerCornerOf(facing.getNormal());
        return new Vec3(side.z, 0, -side.x);
    }
    public static Vec3 point(BlockPos pos, Direction facing, int slot) {
        double x, z;
        if (slot == 0) { x = -0.36; z = 0; }
        else if (slot == 6) { x = 0.36; z = 0; }
        else if (slot < 6) { x = (slot - 3) * 0.16; z = 0.35; }
        else { x = (slot - 8.5) * 0.20; z = -0.39; }
        return top(pos).add(right(facing).scale(x)).add(Vec3.atLowerCornerOf(facing.getNormal()).scale(z));
    }
}
