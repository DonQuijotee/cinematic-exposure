package io.github.donquijotee.cinematic_exposure.client;

/** Assembly geometry in metres about the unrotated body: +X flash side, +Y up, +Z rear.
 * Pure math so the actual paths can be checked without loading Minecraft classes. */
public final class CameraAssembly {
    public static final float YAW = -60;
    public static final float TILT = 12;
    public static final double CONTACT = .70;
    public static final Point LEFT_GRIP = new Point(0, -0.20, -0.16);
    public static final Point RIGHT_REST = new Point(0, -0.20, 0.20);
    public static final Point LEFT_CARRY = new Point(0, -0.06, -0.48);
    public static final Point RIGHT_CARRY = new Point(0, -0.03, 0.42);
    public static final Point FILM_SOCKET = new Point(0, 0.18, 0.20);
    public static final Point FILM_OUTSIDE = new Point(0, 0.31, 0.20);
    public static final Point FILM_GATE = new Point(0, 0.12, 0.40);
    public static final Point FLASH_SOCKET = new Point(0.34, 0, -0.045);
    public static final Point LENS_SOCKET = new Point(0, 0, -0.26);

    public static final Point FLASH_OUTSIDE = FLASH_SOCKET.add(new Point(.22,0,0));
    public static final Point LEFT_TAKE = LEFT_CARRY.add(new Point(0,-.03,.04));
    public static final Point RIGHT_TAKE = RIGHT_CARRY.add(new Point(0,-.03,0));

    public enum Part {
        FILM(.68), FLASH(.40), LENS(.50), FILTER(.44);
        public final double duration;
        Part(double duration) { this.duration = duration; }
    }

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
        Point[] anchors = {LEFT_GRIP,LENS_SOCKET,LEFT_CARRY,LEFT_TAKE,RIGHT_REST,RIGHT_CARRY,
                RIGHT_TAKE,FILM_SOCKET,FILM_OUTSIDE,FILM_GATE,FLASH_SOCKET,FLASH_OUTSIDE};
        double shoulderY = (leftShoulder.y()+rightShoulder.y())/2;
        // Keep the camera near shoulder height; a low body makes the straight sleeve cross the flash.
        for (int pass=0;pass<48;pass++) {
            work = new Point(work.x(),Math.clamp(work.y(),shoulderY-.04,shoulderY+.04),work.z());
            for (int i=0;i<anchors.length;i++) {
                Point shoulder = i < 4 ? leftShoulder : rightShoulder;
                Point hand = work.add(orient(anchors[i]));
                Point delta = hand.subtract(shoulder);
                if (delta.length() > reach) work = work.add(shoulder.add(delta.scale(reach/delta.length())).subtract(hand));
            }
        }
        return work;
    }

    /** Yaw-local body position, shared with the screen and non-game geometry checks. */
    public static Point workPoint(int height, double reach) {
        double scale = Math.clamp(height*.068/18,.6,1.25);
        double top = 6+86*scale+12, cy = (top+height-24)/2;
        double depth = Math.max(.95,.60*height/(2*Math.tan(Math.toRadians(33))*Math.max(64,height-top-24)));
        double down = (2*cy/height-1)*depth*Math.tan(Math.toRadians(33)), pitch = Math.toRadians(18);
        Point desired = new Point(0,-.03-depth*Math.sin(pitch)-down*Math.cos(pitch),
                .045+depth*Math.cos(pitch)-down*Math.sin(pitch));
        return fitWork(desired,new Point(-.38,-.49,.125),new Point(.38,-.49,.125),reach);
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
                rightHand ? InteractionMotion.softBound(cursor.z(), .38, .52, .04)
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

    /** A short placement from the nearby work edge. Film clears the top rear; lenses still twist. */
    public record Placement(Part part, Point start, Point rest, Point take, Point socket, Point outside,
                            Point gate, boolean alreadyHolding) {
        public Point hand(double t) {
            Point approach = part == Part.FILM ? gate : outside;
            if (alreadyHolding) {
                if (t < .30) return move(start,approach,t,0,.30);
            } else {
                if (t < .16) return move(start,take,t,0,.16);
                if (t < .30) return move(take,approach,t,.16,.30);
            }
            if (part == Part.FILM) {
                if (t < .46) return move(gate,outside,t,.30,.46);
                if (t < .62) return move(outside,socket,t,.46,.62);
            } else if (t < .54) return move(outside,socket,t,.30,.54);
            if (t <= CONTACT) return socket;
            if (part == Part.FILM) {
                if (t < .82) return move(socket,outside,t,CONTACT,.82);
                if (t < .92) return move(outside,gate,t,.82,.92);
                return move(gate,rest,t,.92,1);
            }
            if (part == Part.FLASH) {
                if (t < .84) return move(socket,outside,t,CONTACT,.84);
                return move(outside,rest,t,.84,1);
            }
            return move(socket,rest,t,CONTACT,1);
        }
        public double itemScale(double t) { return alreadyHolding ? 1 : ease((t-.10)/.06); }
        public double twist(double t) {
            return part == Part.LENS || part == Part.FILTER ? 90*ease((t-.54)/(CONTACT-.54)) : 0;
        }
    }
}
