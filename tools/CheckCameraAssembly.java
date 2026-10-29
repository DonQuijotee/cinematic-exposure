import io.github.donquijotee.cinematic_exposure.client.CameraAssembly;
import io.github.donquijotee.cinematic_exposure.client.CameraAssembly.Point;

/** Runs only the addon's pure geometry. No Minecraft, renderer, window, client or server is loaded. */
class CheckCameraAssembly {
    // Exposure 1.9.19 camera_parts/flash.json: [1,1,6]..[5,5,8], body center [8,3,8], scale .72.
    static final Point MIN = new Point(.135,-.09,-.09), MAX = new Point(.315,.09,0);
    static double closest = Double.POSITIVE_INFINITY;
    static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static double boxDistance(Point p) {
        double x = Math.max(Math.max(MIN.x()-p.x(),p.x()-MAX.x()),0);
        double y = Math.max(Math.max(MIN.y()-p.y(),p.y()-MAX.y()),0);
        double z = Math.max(Math.max(MIN.z()-p.z(),p.z()-MAX.z()),0);
        return Math.sqrt(x*x+y*y+z*z);
    }
    static void clear(Point p, double radius, String what) {
        double distance = boxDistance(p);
        closest = Math.min(closest,distance-radius);
        require(distance >= radius,what+" intersects flash: distance="+distance+", point="+p);
    }
    // Host wide sleeve: 4.5 px square * .55 / 16, circumscribed radius < .11.
    static void checkHand(Point hand, Point shoulder, boolean shown, String context) {
        clear(hand,.11,"palm "+context);
        if (shown) clear(hand,.156,"film prop "+context);
        for (int k=0;k<=40;k++) clear(shoulder.lerp(hand,k/40.0),.11,"sleeve "+context);
    }
    public static void main(String[] args) {
        Point left = new Point(-.38,-.49,.125), right = new Point(.38,-.49,.125);
        Point pocket = right.add(new Point(0,-.46,.04));
        Point lensAxis = CameraAssembly.orient(new Point(0,0,-1));
        require(lensAxis.x() < -.55 && lensAxis.z() < -.4,"lens must face diagonally left and toward viewer");
        require(CameraAssembly.orient(CameraAssembly.FILM_SOCKET).x() > .05,"film must be on right");
        require(CameraAssembly.orient(CameraAssembly.LEFT_GRIP).x() < 0,"left supports front");
        require(CameraAssembly.orient(CameraAssembly.RIGHT_REST).x() > 0,"right supports rear");
        require(CameraAssembly.FILM_SOCKET.y() > .135 && CameraAssembly.FILM_SOCKET.z() > .09,"film enters top rear");
        Point filmAxis = CameraAssembly.FILM_OUTSIDE.subtract(CameraAssembly.FILM_SOCKET);
        require(filmAxis.y() > .15 && Math.abs(filmAxis.x())+Math.abs(filmAxis.z()) < 1e-10,"film insertion must be along local top axis");
        int samples = 0;
        for (double y : new double[]{-.32,-.5,-.8}) for (double z : new double[]{.65,.85,1.3}) {
            Point work = CameraAssembly.fitWork(new Point(0,y,z),left,right,.74);
            Point socket = work.add(CameraAssembly.orient(CameraAssembly.FILM_SOCKET));
            Point outside = work.add(CameraAssembly.orient(CameraAssembly.FILM_OUTSIDE));
            Point gate = work.add(CameraAssembly.orient(CameraAssembly.FILM_GATE));
            Point grip = work.add(CameraAssembly.orient(CameraAssembly.RIGHT_REST));
            Point carry = work.add(CameraAssembly.orient(CameraAssembly.RIGHT_CARRY));
            require(work.add(CameraAssembly.orient(CameraAssembly.LEFT_GRIP)).subtract(left).length() <= .74001,"left grip reach");
            for (Point anchor : new Point[]{socket,outside,gate,grip,carry})
                require(anchor.subtract(right).length() <= .74001,"film corridor exceeds reach");
            java.util.List<Point> starts = new java.util.ArrayList<>(java.util.List.of(
                    CameraAssembly.RIGHT_REST,CameraAssembly.RIGHT_CARRY,CameraAssembly.FILM_SOCKET));
            for (boolean rightHand : new boolean[]{true,false}) {
                Point shoulder = rightHand ? right : left;
                Point anchor = rightHand ? carry : work.add(CameraAssembly.orient(CameraAssembly.LEFT_CARRY));
                for (int ix=0;ix<=6;ix++) for (int iy=0;iy<=6;iy++) for (int iz=0;iz<=6;iz++) {
                    Point cursor = new Point(-.5+ix/6.0,-.5+iy/6.0,-1+iz/3.0);
                    Point raw = work.add(CameraAssembly.orient(CameraAssembly.freeTarget(cursor,rightHand)));
                    Point free = CameraAssembly.reachableFrom(anchor,raw,shoulder,.74);
                    require(free.subtract(shoulder).length() <= .74001,"cursor hand reach");
                    Point local = CameraAssembly.unorient(free.subtract(work));
                    checkHand(local,CameraAssembly.unorient(shoulder.subtract(work)),true,"cursor zone");
                    if (rightHand && (ix==0 || ix==6) && (iy==0 || iy==6) && (iz==0 || iz==6)) starts.add(local);
                }
            }
            for (Point startAnchor : starts) {
                Point start = work.add(CameraAssembly.orient(startAnchor));
                for (boolean inserting : new boolean[]{true,false}) for (boolean holding : new boolean[]{true,false}) {
                    Point rest = inserting ? grip : carry;
                    var path = new CameraAssembly.FilmPath(start,rest,pocket,socket,outside,gate,holding);
                    require(path.hand(0,inserting).subtract(start).length()<1e-9,"start continuity");
                    require(path.hand(1,inserting).subtract(rest).length()<1e-9,"end continuity");
                    for (double boundary : new double[]{.16,.20,.28,.36,.43,.48,.50,.65,.68,.74,.82,.86,.94})
                        require(path.hand(boundary-1e-7,inserting).subtract(path.hand(boundary+1e-7,inserting)).length()<1e-4,"phase continuity");
                    Point shoulder = CameraAssembly.unorient(right.subtract(work));
                    for (int i=0;i<=400;i++) {
                        double t = i/400.0;
                        Point hand = CameraAssembly.unorient(path.hand(t,inserting).subtract(work));
                        boolean shown = inserting ? (holding || t>=.18) && t<.74 : t>=.43;
                        // Sleeve envelope along the full forearm, including the shoulder end.
                        checkHand(hand,shoulder,shown,"work="+work+", t="+t);
                        samples++;
                    }
                    for (int fps : new int[]{30,60,144}) for (double speed : new double[]{.25,1,3}) {
                        Point smoothed = start;
                        double dt = speed/fps, alpha = 1-Math.exp(-dt*20);
                        for (double time=dt;time<CameraAssembly.FILM_DURATION+0.3;time+=dt) {
                            double t = Math.min(1,time/CameraAssembly.FILM_DURATION);
                            smoothed = smoothed.lerp(path.hand(t,inserting),alpha);
                            boolean shown = inserting ? (holding || t>=.18) && t<.74 : t>=.43;
                            checkHand(CameraAssembly.unorient(smoothed.subtract(work)),shoulder,shown,"work="+work+", smoothed t="+t);
                            samples++;
                        }
                    }
                }
            }
            // Diagonal lens work belongs to the left arm, with the right resting under the rear.
            Point leftShoulder = CameraAssembly.unorient(left.subtract(work));
            for (Point anchor : new Point[]{CameraAssembly.LEFT_GRIP,CameraAssembly.LEFT_CARRY,
                    CameraAssembly.LENS_SOCKET,new Point(0,0,-.48)}) {
                Point worldAnchor = work.add(CameraAssembly.orient(anchor));
                require(worldAnchor.subtract(left).length() <= .74001,"left lens path reach");
                checkHand(anchor,leftShoulder,false,"left grip/lens path");
            }
            Point probe = new Point(.2,-.3,.1);
            require(CameraAssembly.unorient(CameraAssembly.orient(probe)).subtract(probe).length()<1e-12,"pose inverse");
        }
        System.out.println("PASS: "+samples+" film-path samples; pose, reach, phase continuity and stock flash clearance; min margin="+closest);
    }
}
