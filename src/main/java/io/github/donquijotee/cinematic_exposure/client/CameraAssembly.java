package io.github.donquijotee.cinematic_exposure.client;

/** Assembly geometry in metres about the unrotated body: +X flash side, +Y up, +Z rear.
 * Pure math so the actual paths can be checked without loading Minecraft classes. */
public final class CameraAssembly {
    public static final float YAW = -60;
    public static final float TILT = 12;
    public static final double FILM_DURATION = 1.6;
    public static final Point LEFT_GRIP = new Point(0, -0.20, -0.16);
    public static final Point RIGHT_REST = new Point(0, -0.20, 0.20);
    public static final Point LEFT_CARRY = new Point(0, -0.06, -0.48);
    public static final Point RIGHT_CARRY = new Point(0, -0.03, 0.46);
    public static final Point FILM_SOCKET = new Point(0, 0.18, 0.20);
    public static final Point FILM_OUTSIDE = new Point(0, 0.39, 0.20);
    public static final Point FILM_GATE = new Point(0, 0.12, 0.44);
    public static final Point FLASH_SOCKET = new Point(0.34, 0, -0.045);
    public static final Point LENS_SOCKET = new Point(0, 0, -0.26);

    private CameraAssembly() {}

    public record Point(double x, double y, double z) {
        public Point add(Point b) { return new Point(x+b.x,y+b.y,z+b.z); }
        public Point subtract(Point b) { return new Point(x-b.x,y-b.y,z-b.z); }
        public Point scale(double k) { return new Point(x*k,y*k,z*k); }
        public double dot(Point b) { return x*b.x+y*b.y+z*b.z; }
        public Point cross(Point b) { return new Point(y*b.z-z*b.y,z*b.x-x*b.z,x*b.y-y*b.x); }
        public Point lerp(Point b, double k) { return add(b.subtract(this).scale(k)); }
        public double length() { return Math.sqrt(x*x+y*y+z*z); }
    }

    /** Same Y-then-X model matrix as SceneDrawing; the lens faces diagonally toward the viewer, with the film opening at the top rear. */
    public static Point orient(Point p) { return yaw(pitch(p,TILT),YAW); }
    public static Point unorient(Point p) { return pitch(yaw(p,-YAW),-TILT); }
    private static Point pitch(Point p, double degrees) {
        double c = Math.cos(Math.toRadians(degrees)), s = Math.sin(Math.toRadians(degrees));
        return new Point(p.x,c*p.y-s*p.z,s*p.y+c*p.z);
    }
    private static Point yaw(Point p, double degrees) {
        double c = Math.cos(Math.toRadians(degrees)), s = Math.sin(Math.toRadians(degrees));
        // Local +X is opposite the model's +X, hence this sign convention.
        return new Point(c*p.x-s*p.z,p.y,s*p.x+c*p.z);
    }

    /** Fit the body and both grips in a yaw-aligned frame, keeping the authored film path reachable. */
    public static Point fitWork(Point work, Point leftShoulder, Point rightShoulder, double reach) {
        Point[] anchors = {LEFT_GRIP,LENS_SOCKET,LEFT_CARRY,RIGHT_REST,RIGHT_CARRY,FILM_SOCKET,FILM_OUTSIDE,FILM_GATE};
        double shoulderY = (leftShoulder.y()+rightShoulder.y())/2;
        // Keep the camera near shoulder height; a low body makes the straight sleeve cross the flash.
        for (int pass=0;pass<48;pass++) {
            work = new Point(work.x(),Math.clamp(work.y(),shoulderY-.04,shoulderY+.16),work.z());
            for (int i=0;i<anchors.length;i++) {
                Point shoulder = i < 3 ? leftShoulder : rightShoulder;
                Point hand = work.add(orient(anchors[i]));
                Point delta = hand.subtract(shoulder);
                if (delta.length() > reach) work = work.add(shoulder.add(delta.scale(reach/delta.length())).subtract(hand));
            }
        }
        return work;
    }

    public static double ease(double t) {
        t = Math.clamp(t,0,1);
        return t*t*t*(t*(t*6-15)+10);
    }
    private static Point move(Point a, Point b, double t, double from, double to) {
        return a.lerp(b,ease((t-from)/(to-from)));
    }

    /** Free carrying stays in front of the lens or behind the body, clear of the mounted flash. */
    public static Point freeTarget(Point cursor, boolean rightHand) {
        return new Point(InteractionMotion.softBound(cursor.x(), -.09, .09, .04),
                InteractionMotion.softBound(cursor.y(), -.10, .18, .06),
                rightHand ? InteractionMotion.softBound(cursor.z(), .40, .56, .04)
                        : InteractionMotion.softBound(cursor.z(), -.56, -.40, .04));
    }

    public static Point reachableFrom(Point anchor, Point target, Point shoulder, double reach) {
        if (target.subtract(shoulder).length() <= reach) return target;
        Point from = anchor.subtract(shoulder);
        if (from.length() > reach) return shoulder.add(from.scale(reach/from.length()));
        double low = 0, high = 1;
        for (int i=0;i<24;i++) {
            double mid = (low+high)/2;
            if (anchor.lerp(target,mid).subtract(shoulder).length() <= reach) low = mid; else high = mid;
        }
        return anchor.lerp(target,low);
    }

    /** Approach above the rear, lower into the top opening, and withdraw along the same safe corridor. */
    public record FilmPath(Point start, Point rest, Point pocket, Point socket, Point outside, Point gate,
                           boolean alreadyHolding) {
        public Point hand(double t, boolean inserting) {
            if (inserting) {
                if (alreadyHolding) {
                    if (t < 0.36) return move(start,gate,t,0,0.36);
                } else {
                    if (t < 0.16) return move(start,pocket,t,0,0.16);
                    if (t < 0.20) return pocket;
                    if (t < 0.36) return move(pocket,gate,t,0.20,0.36);
                }
                if (t < 0.48) return move(gate,outside,t,0.36,0.48);
                if (t < 0.65) return move(outside,socket,t,0.48,0.65);
                if (t < 0.74) return socket;
                if (t < 0.86) return move(socket,outside,t,0.74,0.86);
                if (t < 0.94) return move(outside,gate,t,0.86,0.94);
                return move(gate,rest,t,0.94,1);
            }
            if (t < 0.16) return move(start,gate,t,0,0.16);
            if (t < 0.28) return move(gate,outside,t,0.16,0.28);
            if (t < 0.43) return move(outside,socket,t,0.28,0.43);
            if (t < 0.50) return socket;
            if (t < 0.68) return move(socket,outside,t,0.50,0.68);
            if (t < 0.82) return move(outside,gate,t,0.68,0.82);
            return move(gate,rest,t,0.82,1);
        }
    }
}
