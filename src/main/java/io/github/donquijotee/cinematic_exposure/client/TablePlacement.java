package io.github.donquijotee.cinematic_exposure.client;

import io.github.donquijotee.cinematic_exposure.client.CameraAssembly.Point;

/** Table-style placement: take at the near edge, carry over the slot, set down, lift away. */
public final class TablePlacement {
    public static final double DURATION = .36;
    public static final double DROP = .64;
    public static final double HAND_Y = .062;
    public static final double ITEM_REACH = .075;

    private TablePlacement() {}

    public static Point take(Point rest, Point side) {
        return rest.add(side.scale(.14)).add(new Point(0,.03,0));
    }

    public record Path(Point start, Point rest, Point take, Point grip, boolean alreadyHeld) {
        public Point hand(double t) {
            Point over = grip.add(new Point(0,.06,0));
            if (alreadyHeld) {
                if (t < .58) return move(start,over,t,0,.58);
            } else {
                if (t < .28) return move(start,take,t,0,.28);
                if (t < .58) {
                    double k = CameraAssembly.ease((t-.28)/.30);
                    return take.lerp(over,k).add(new Point(0,.05*Math.sin(Math.PI*k),0));
                }
            }
            if (t < DROP) return move(over,grip,t,.58,DROP);
            if (t < .70) return move(grip,over,t,DROP,.70);
            return move(over,rest,t,.70,1);
        }
        public double itemScale(double t) {
            return alreadyHeld ? 1-.28*CameraAssembly.ease(t/DROP)
                    : .72*CameraAssembly.ease((t-.24)/.08);
        }
    }

    /** Shared gate for input and preview; contacted does not end the hand's release stroke. */
    public static boolean busy(boolean active, boolean done) { return active && !done; }
    public static boolean hidesFilm(boolean active, int slot, boolean contacted, boolean done) {
        return busy(active,done) && slot == 0 && !contacted;
    }

    private static Point move(Point a, Point b, double t, double from, double to) {
        return a.lerp(b,CameraAssembly.ease((t-from)/(to-from)));
    }
}
