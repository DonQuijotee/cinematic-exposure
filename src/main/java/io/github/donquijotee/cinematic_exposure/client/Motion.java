package io.github.donquijotee.cinematic_exposure.client;

import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** A visual snapshot, never a reference to a mutable menu stack. No gameplay callbacks exist in this timeline. */
public final class Motion {
    public enum Kind { FILM, FLASH, LENS, FILTER, PRINT, PHOTO }
    public final Kind kind;
    public final boolean inserting;
    public final ItemStack item;
    private double time;
    public final double duration;

    public Motion(Kind kind, boolean inserting, ItemStack item) {
        this.kind = kind;
        this.inserting = inserting;
        this.item = item.copy();
        duration = kind == Kind.LENS ? 1.35 : kind == Kind.PRINT ? 1.1 : 0.95;
    }

    public void advance(double dt) { time += dt; }
    public boolean done() { return time >= duration; }
    public double progress() { return Mth.clamp(time / duration, 0, 1); }
    public static double ease(double t) { t = Mth.clamp(t, 0, 1); return t * t * t * (t * (t * 6 - 15) + 10); }
    public static double window(double t, double a, double b) { return ease((t - a) / (b - a)); }

    /** pickup -> align -> twist/seat -> release; removal traverses the same path backwards. */
    public Vec3 hand(Vec3 rest, Vec3 pickup, Vec3 socket) {
        double u = progress();
        if (kind == Kind.PRINT) {
            double reach = window(u, 0, 0.30) * (1 - window(u, 0.65, 1));
            return rest.lerp(socket, reach).add(0, -0.035 * Math.sin(window(u, 0.3, 0.65) * Math.PI), 0);
        }
        double k = inserting ? u : 1 - u;
        Vec3 travel = pickup.lerp(socket, window(k, 0.12, 0.52))
                .add(0, 0.075 * Math.sin(window(k, 0.12, 0.52) * Math.PI), 0);
        if (inserting) return rest.lerp(travel, window(u, 0, 0.12)).lerp(rest, window(u, 0.82, 1));
        return rest.lerp(travel, window(u, 0, 0.18)).lerp(rest, window(u, 0.88, 1));
    }

    public boolean showsItem() {
        double u = progress();
        return kind != Kind.PRINT && (inserting ? u >= 0.10 && u <= 0.78 : u >= 0.20 && u <= 0.90);
    }

    public float twist() {
        double u = inserting ? progress() : 1 - progress();
        return kind == Kind.LENS || kind == Kind.FILTER ? (float) (90 * window(u, 0.52, 0.78)) : 0;
    }
}
