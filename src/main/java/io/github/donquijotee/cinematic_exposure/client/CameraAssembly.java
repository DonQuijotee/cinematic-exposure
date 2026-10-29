package io.github.donquijotee.cinematic_exposure.client;

/** Assembly geometry in metres about the camera body: +X right, +Y up, +Z away from the viewer.
 * Pure math so the actual paths can be checked without loading Minecraft classes. */
public final class CameraAssembly {
    public static final float TILT = 26;
    public static final Point LEFT_GRIP = new Point(-0.13, -0.20, 0.02);
    public static final Point RIGHT_REST = new Point(0.40, -0.29, -0.30);
    public static final Point FILM_SOCKET = new Point(0.18, -0.35, 0.10);
    public static final Point FILM_OUTSIDE = new Point(0.38, -0.36, 0.10);
    public static final Point FILM_CLEARANCE = new Point(0.52, -0.36, -0.32);
    public static final Point FILM_IN_HAND = new Point(0,0.10,0);
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

    /** Same X-axis rotation as the model renderer, applied to all grip and attachment anchors. */
    public static Point tilt(Point p) { return rotate(p, TILT); }
    public static Point untilt(Point p) { return rotate(p, -TILT); }
    private static Point rotate(Point p, double degrees) {
        double c = Math.cos(Math.toRadians(degrees)), s = Math.sin(Math.toRadians(degrees));
        return new Point(p.x, c*p.y-s*p.z, s*p.y+c*p.z);
    }

    /** Fit the body and both grips in a yaw-aligned frame, keeping the authored film path reachable. */
    public static Point fitWork(Point work, Point leftShoulder, Point rightShoulder, double reach) {
        Point[] anchors = {LEFT_GRIP,RIGHT_REST,FILM_SOCKET,FILM_OUTSIDE,FILM_CLEARANCE};
        for (int pass=0;pass<6;pass++) for (int i=0;i<anchors.length;i++) {
            Point shoulder = i == 0 ? leftShoulder : rightShoulder;
            Point hand = work.add(tilt(anchors[i]));
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

    /** Explicit under-body corridor. Never take a diagonal shortcut from the pocket to the film socket. */
    public record FilmPath(Point start, Point rest, Point pocket, Point socket, Point outside,
                           Point clearance, boolean alreadyHolding) {
        public Point hand(double t, boolean inserting) {
            if (inserting) {
                if (alreadyHolding) {
                    if (t < 0.42) return move(start,clearance,t,0,0.42);
                } else {
                    if (t < 0.18) return move(start,pocket,t,0,0.18);
                    if (t < 0.24) return pocket;
                    if (t < 0.42) return move(pocket,clearance,t,0.24,0.42);
                }
                if (t < 0.56) return move(clearance,outside,t,0.42,0.56);
                if (t < 0.70) return move(outside,socket,t,0.56,0.70);
                if (t < 0.80) return socket;
                if (t < 0.90) return move(socket,outside,t,0.80,0.90);
                if (t < 0.96) return move(outside,clearance,t,0.90,0.96);
                return move(clearance,rest,t,0.96,1);
            }
            if (t < 0.10) return move(start,clearance,t,0,0.10);
            if (t < 0.23) return move(clearance,outside,t,0.10,0.23);
            if (t < 0.36) return move(outside,socket,t,0.23,0.36);
            if (t < 0.44) return socket;
            if (t < 0.65) return move(socket,outside,t,0.44,0.65);
            if (t < 0.84) return move(outside,clearance,t,0.65,0.84);
            return move(clearance,rest,t,0.84,1);
        }
    }
}
