import io.github.donquijotee.cinematic_exposure.client.CameraAssembly;
import io.github.donquijotee.cinematic_exposure.client.CameraAssembly.Point;
import io.github.donquijotee.cinematic_exposure.client.CameraAssembly.Part;
import io.github.donquijotee.cinematic_exposure.client.HandSpring;
import io.github.donquijotee.cinematic_exposure.client.TransferTimeline;
import io.github.donquijotee.cinematic_exposure.client.PlacementInput;

/** Pure production geometry/controllers only. No Minecraft, renderer, window, client or server. */
class CheckCameraAssembly {
    // Exposure 1.9.19 camera_parts/flash.json: [1,1,6]..[5,5,8], body center [8,3,8], scale .72.
    static final Point MIN = new Point(.135,-.09,-.09), MAX = new Point(.315,.09,0);
    static final Point LEFT = new Point(-.38,-.49,.125), RIGHT = new Point(.38,-.49,.125);
    static double closest = Double.POSITIVE_INFINITY, longest = 0;
    static int samples;
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
    static Point at(Point work, Point local) { return work.add(CameraAssembly.orient(local)); }
    static Point limit(Point hand, Point shoulder) {
        Point delta = hand.subtract(shoulder);
        return delta.length() > .74 ? shoulder.add(delta.scale(.74/delta.length())) : hand;
    }
    static void checkHand(Point world, Point shoulder, Point work, boolean shown, Part part) {
        require(world.subtract(shoulder).length() <= .74001,"hand exceeds reach: "+part);
        // A flash being installed occupies its own socket; film and lens work must clear mounted flash.
        if (part != Part.FLASH) {
            Point hand = CameraAssembly.unorient(world.subtract(work));
            Point localShoulder = CameraAssembly.unorient(shoulder.subtract(work));
            clear(hand,.11,"palm "+part);
            if (shown) clear(hand,.156,"prop "+part);
            for (int k=0;k<=40;k++) clear(localShoulder.lerp(hand,k/40.0),.11,"sleeve "+part);
        }
        samples++;
    }
    static void placement(Point work, Point start, Point follow, Part part, boolean holding) {
        boolean right = part == Part.FILM || part == Part.FLASH;
        Point shoulder = right ? RIGHT : LEFT;
        Point rest = at(work,right ? CameraAssembly.RIGHT_REST : CameraAssembly.LEFT_GRIP);
        Point take = at(work,right ? CameraAssembly.RIGHT_TAKE : CameraAssembly.LEFT_TAKE);
        Point socket = at(work,switch (part) {
            case FILM -> CameraAssembly.FILM_SOCKET;
            case FLASH -> CameraAssembly.FLASH_SOCKET;
            default -> CameraAssembly.LENS_SOCKET;
        });
        Point outside = at(work,switch (part) {
            case FILM -> CameraAssembly.FILM_OUTSIDE;
            case FLASH -> CameraAssembly.FLASH_OUTSIDE;
            default -> CameraAssembly.LENS_SOCKET.add(new Point(0,0,part == Part.FILTER ? -.12 : -.22));
        });
        var path = new CameraAssembly.Placement(part,start,rest,take,socket,outside,at(work,CameraAssembly.FILM_GATE),holding);
        require(path.hand(0).subtract(start).length()<1e-9,"start continuity");
        require(path.hand(1).subtract(rest).length()<1e-9,"end continuity");
        require(path.hand(CameraAssembly.CONTACT).subtract(socket).length()<1e-9,"seat at contact");
        require(take.subtract(rest).length()<.35,"take must stay near working hand, not below viewport");
        for (double boundary : new double[]{.16,.30,.46,.54,.62,.70,.82,.84,.92})
            require(path.hand(boundary-1e-7).subtract(path.hand(boundary+1e-7)).length()<1e-4,"phase continuity");
        if (part == Part.LENS || part == Part.FILTER) require(path.twist(CameraAssembly.CONTACT)==90,"lens must fasten");
        for (int i=0;i<=200;i++) {
            double t = i/200.0;
            try { checkHand(path.hand(t),shoulder,work,path.itemScale(t)>.001 && t<CameraAssembly.CONTACT,part); }
            catch (AssertionError e) { throw new AssertionError("work="+work+", start="+start+", held="+holding+", t="+t,e); }
        }
        for (int fps : new int[]{30,60,144}) for (double speed : new double[]{.25,1,3}) {
            var spring = new HandSpring(start);
            var timing = new TransferTimeline(part.duration,CameraAssembly.CONTACT);
            double dt = speed/fps, elapsed = 0;
            for (int frame=0;!timing.done() && frame<5000;frame++) {
                timing.advance(dt);
                double t = timing.progress(), tail = part == Part.FILM ? .92 : .90;
                Point target = path.hand(t).lerp(follow,CameraAssembly.ease((t-tail)/(1-tail)));
                spring.update(limit(target,shoulder),dt);
                Point smoothed = spring.limit(shoulder,.74);
                timing.touch(smoothed.subtract(socket).length()<.045);
                checkHand(smoothed,shoulder,work,path.itemScale(t)>.001 && !timing.contacted(),part);
                elapsed += dt;
            }
            require(timing.contacted(),part+" did not contact before timeout; work="+work+", start="+start);
            require(elapsed<1.15,"placement should finish promptly: "+elapsed);
            longest = Math.max(longest,elapsed);
        }
    }
    static void input() {
        var gate = new PlacementInput();
        require(!gate.rejectPress(0,false),"idle press allowed");
        gate.finish(() -> {
            require(gate.finishing(),"original admitted transaction can finish all its menu clicks");
            gate.finish(() -> { require(gate.finishing(),"nested native call"); return true; });
            require(gate.finishing(),"nested completion retains outer admission");
            return true;
        });
        require(!gate.finishing(),"future input must be gated again");
        require(gate.rejectPress(0,true),"busy press refused");
        require(gate.rejectDrag(0,false),"refused press cannot drag after animation");
        require(gate.rejectRelease(0,false),"refused press cannot place on late release");
        require(!gate.rejectPress(0,false) && !gate.rejectRelease(0,false),"next new gesture allowed");
        require(gate.rejectRelease(1,true),"ongoing press release ignored during placement");
        try { gate.finish(() -> { throw new IllegalStateException("check cleanup"); }); }
        catch (IllegalStateException expected) { }
        require(!gate.finishing(),"input failure must restore admission gate");
    }
    public static void main(String[] args) {
        input();
        Point lensAxis = CameraAssembly.orient(new Point(0,0,-1));
        require(lensAxis.x()<-.55 && lensAxis.z()<-.4,"diagonal lens presentation");
        require(CameraAssembly.orient(CameraAssembly.FILM_SOCKET).x()>.05,"film on right");
        Point filmAxis = CameraAssembly.FILM_OUTSIDE.subtract(CameraAssembly.FILM_SOCKET);
        require(filmAxis.y()>.10 && Math.abs(filmAxis.x())+Math.abs(filmAxis.z())<1e-10,"film enters vertically at top rear");
        int[] heights = {180,240,360,540,1080};
        // Captured actual fitted distances in v0.2.12, not the unbounded desired depth.
        double[] previousDistance = {.71653669,.71061598,.69391565,.66577655,.63867080};
        for (int size=0;size<heights.length;size++) {
            Point work = CameraAssembly.workPoint(heights[size],.74);
            double distance = work.subtract(new Point(0,-.03,.045)).length();
            require(distance>previousDistance[size]+.015,"camera must actually be farther after reach fitting");
            require(distance<previousDistance[size]+.14,"distance change should be modest");
            for (boolean right : new boolean[]{true,false}) {
                Point shoulder = right ? RIGHT : LEFT;
                Point rest = at(work,right ? CameraAssembly.RIGHT_REST : CameraAssembly.LEFT_GRIP);
                Point carry = at(work,right ? CameraAssembly.RIGHT_CARRY : CameraAssembly.LEFT_CARRY);
                var starts = new java.util.ArrayList<Point>(java.util.List.of(rest,carry));
                for (int ix=0;ix<=6;ix++) for (int iy=0;iy<=6;iy++) for (int iz=0;iz<=6;iz++) {
                    Point raw = at(work,CameraAssembly.freeTarget(new Point(-.5+ix/6.0,-.5+iy/6.0,-1+iz/3.0),right));
                    Point free = CameraAssembly.reachableFrom(carry,raw,shoulder,.74);
                    checkHand(free,shoulder,work,true,Part.FILM);
                    if ((ix==0 || ix==6) && (iy==0 || iy==6) && (iz==0 || iz==6)) starts.add(free);
                }
                for (Point start : starts) for (boolean holding : new boolean[]{true,false})
                    for (Part part : right ? new Part[]{Part.FILM,Part.FLASH} : new Part[]{Part.LENS,Part.FILTER})
                        for (Point follow : new Point[]{rest,carry,start}) placement(work,start,follow,part,holding);
            }
            System.out.printf("PASS: GUI height %d, actual camera distance %.3f -> %.3f m%n",heights[size],previousDistance[size],distance);
        }
        System.out.println("PASS: "+samples+" camera samples; near-edge placement, all sockets, flash clearance, reach, continuity and input admission; min margin="+closest+", max placement seconds="+longest);
    }
}
