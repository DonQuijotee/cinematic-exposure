package io.github.donquijotee.cinematic_exposure.client;

/** Assembly geometry in metres about the unrotated body: +X flash side, +Y up, +Z rear.
 * Pure math so the actual paths can be checked without loading Minecraft classes. */
public final class CameraAssembly {
    public static final float YAW = -80;
    public static final float TILT = 8;
    public static final double FILM_DURATION = 1.15;
    public static final Point LEFT_GRIP = new Point(0, -0.20, -0.16);
    public static final Point RIGHT_REST = new Point(0, -0.20, 0.13);
    public static final Point LEFT_CARRY = new Point(0, -0.10, -0.43);
    public static final Point RIGHT_CARRY = new Point(0.10, -0.12, 0.40);
    public static final Point FILM_SOCKET = new Point(0, -0.06, 0.27);
    public static final Point FILM_OUTSIDE = new Point(0, -0.06, 0.46);
    public static final Point FLASH_SOCKET = new Point(0.34, 0, -0.045);
    public static final Point LENS_SOCKET = new Point(0, 0, -0.26);

    private CameraAssembly() {}

    public record Point(double x, double y, double z) {
        public Point add(Point b) { return new Point(x+b.x,y+b.y,z+b.z); }
        public Point subtract(Point b) { return new Point(x-b.x,y-b.y,z-b.z); }
        public Point scale(double k) { return new Point(x*k,y*k,z*k); }
        public Point lerp(Point b, double k) { return add(b.subtract(this).scale(k)); }
        public double length() { return Math.sqrt(x*x+y*y+z*z); }
    }

    /** Same Y-then-X model matrix as SceneDrawing; the lens points left and the film back points right. */
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
        Point[] anchors = {LEFT_GRIP,LENS_SOCKET,LEFT_CARRY,RIGHT_REST,RIGHT_CARRY,FILM_SOCKET,FILM_OUTSIDE};
        for (int pass=0;pass<6;pass++) for (int i=0;i<anchors.length;i++) {
            Point shoulder = i < 3 ? leftShoulder : rightShoulder;
            Point hand = work.add(orient(anchors[i]));
            Point delta = hand.subtract(shoulder);
            if (delta.length() > reach) work = work.add(shoulder.add(delta.scale(reach/delta.length())).subtract(hand));
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

    /** Short rear-facing transfer. No under-body detour or extra outward-return loops. */
    public record FilmPath(Point start, Point rest, Point pocket, Point socket, Point outside,
                           boolean alreadyHolding) {
        public Point hand(double t, boolean inserting) {
            if (inserting) {
                if (alreadyHolding) {
                    if (t < 0.40) return move(start,outside,t,0,0.40);
                } else {
                    if (t < 0.18) return move(start,pocket,t,0,0.18);
                    if (t < 0.24) return pocket;
                    if (t < 0.40) return move(pocket,outside,t,0.24,0.40);
                }
                if (t < 0.65) return move(outside,socket,t,0.40,0.65);
                if (t < 0.80) return socket;
                return move(socket,rest,t,0.80,1);
            }
            if (t < 0.22) return move(start,socket,t,0,0.22);
            if (t < 0.35) return socket;
            if (t < 0.65) return move(socket,outside,t,0.35,0.65);
            return move(outside,rest,t,0.65,1);
        }
    }
}
