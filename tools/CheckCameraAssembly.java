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
        if (shown) clear(hand.add(CameraAssembly.FILM_IN_HAND),.156,"film prop "+context);
        for (int k=0;k<=40;k++) clear(shoulder.lerp(hand,k/40.0),.11,"sleeve "+context);
    }
    public static void main(String[] args) {
        Point left = new Point(-.38,-.49,.125), right = new Point(.38,-.49,.125);
        Point pocket = right.add(new Point(0,-.46,.04));
        int samples = 0;
        for (double y : new double[]{-.32,-.5,-.8}) for (double z : new double[]{.65,.85,1.3}) {
            Point work = CameraAssembly.fitWork(new Point(0,y,z),left,right,.74);
            Point socket = work.add(CameraAssembly.tilt(CameraAssembly.FILM_SOCKET));
            Point outside = work.add(CameraAssembly.tilt(CameraAssembly.FILM_OUTSIDE));
            Point clearance = work.add(CameraAssembly.tilt(CameraAssembly.FILM_CLEARANCE));
            Point rest = work.add(CameraAssembly.tilt(CameraAssembly.RIGHT_REST));
            require(work.add(CameraAssembly.tilt(CameraAssembly.LEFT_GRIP)).subtract(left).length() <= .74001,"left grip reach");
            for (Point anchor : new Point[]{socket,outside,clearance,rest})
                require(anchor.subtract(right).length() <= .74001,"film corridor exceeds reach");
            for (Point startAnchor : new Point[]{CameraAssembly.RIGHT_REST,CameraAssembly.LENS_SOCKET,new Point(.48,0,-.045)}) {
                Point start = work.add(CameraAssembly.tilt(startAnchor));
                for (boolean inserting : new boolean[]{true,false}) for (boolean holding : new boolean[]{true,false}) {
                    var path = new CameraAssembly.FilmPath(start,rest,pocket,socket,outside,clearance,holding);
                    require(path.hand(0,inserting).subtract(start).length()<1e-9,"start continuity");
                    require(path.hand(1,inserting).subtract(rest).length()<1e-9,"end continuity");
                    for (double boundary : new double[]{.1,.13,.14,.18,.20,.23,.24,.36,.38,.4,.42,.44,.56,.58,.62,.65,.7,.8,.82,.84,.9,.96})
                        require(path.hand(boundary-1e-7,inserting).subtract(path.hand(boundary+1e-7,inserting)).length()<1e-4,"phase continuity");
                    Point shoulder = CameraAssembly.untilt(right.subtract(work));
                    for (int i=0;i<=400;i++) {
                        double t = i/400.0;
                        Point hand = CameraAssembly.untilt(path.hand(t,inserting).subtract(work));
                        boolean shown = inserting ? (holding || t>=.21) && t<.80 : t>=.36;
                        // Sleeve envelope along the full forearm, including the shoulder end.
                        checkHand(hand,shoulder,shown,"t="+t);
                        samples++;
                    }
                    for (int fps : new int[]{30,60,144}) for (double speed : new double[]{.25,1,3}) {
                        Point smoothed = start;
                        double dt = speed/fps, alpha = 1-Math.exp(-dt*20);
                        for (double time=dt;time<1.45+0.3;time+=dt) {
                            double t = Math.min(1,time/1.45);
                            smoothed = smoothed.lerp(path.hand(t,inserting),alpha);
                            boolean shown = inserting ? (holding || t>=.21) && t<.80 : t>=.36;
                            checkHand(CameraAssembly.untilt(smoothed.subtract(work)),shoulder,shown,"smoothed t="+t);
                            samples++;
                        }
                    }
                }
            }
            Point probe = new Point(.2,-.3,.1);
            require(CameraAssembly.untilt(CameraAssembly.tilt(probe)).subtract(probe).length()<1e-12,"pose inverse");
        }
        System.out.println("PASS: "+samples+" film-path samples; pose, reach, phase continuity and stock flash clearance; min margin="+closest);
    }
}
