import io.github.donquijotee.cinematic_exposure.client.ArmContinuity;
import io.github.donquijotee.cinematic_exposure.client.CameraAssembly.Point;
import io.github.donquijotee.cinematic_exposure.client.HandSpring;
import io.github.donquijotee.cinematic_exposure.client.TransferDisplay;
import io.github.donquijotee.cinematic_exposure.client.TransferTimeline;

/** Event-order and pose regression checks. Only addon pure-Java classes; never bootstraps Minecraft. */
class CheckTransfers {
    record Stack(String item, int count, String data) {}
    static final Stack EMPTY = new Stack("",0,"");
    static void require(boolean value, String why) { if (!value) throw new AssertionError(why); }
    static void near(Point a, Point b, String why) { require(a.subtract(b).length()<1e-9,why); }
    public static void main(String[] args) {
        int samples = 0;
        // Shift leaves carried empty, but the slot's whole-stack delta still has a complete handoff.
        for (String item : new String[]{"film","paper","cyan","magenta","yellow","black","photo","lens","filter","flash"})
            for (int size : new int[]{1,16,64}) for (boolean insert : new boolean[]{true,false})
                for (int fps : new int[]{30,60,144}) for (double speed : new double[]{.25,1,3}) {
                    Stack full = new Stack(item,size,"component-A");
                    Stack before = insert ? EMPTY : full, after = insert ? full : EMPTY;
                    var display = new TransferDisplay<>(before,after,Stack::equals);
                    var time = new TransferTimeline(1,insert ? .7 : .26);
                    double dt = speed/fps;
                    for (int f=0;!time.done() && f<5000;f++) {
                        time.advance(dt);
                        double was = time.progress();
                        // Both full-content confirmations and individual-slot acknowledgements are idempotent.
                        require(display.reconcile(new Stack(after.item(),after.count(),after.data())),"ack cancelled transfer");
                        require(time.progress() == was,"ack restarted gesture");
                        // Deliberately lag behind the authored contact time, like a real smoothed hand.
                        time.touch(f*dt >= (insert ? .7 : .26)+.10);
                        Stack resting = display.resting(after,time.contacted());
                        boolean inHand = insert ? !time.contacted() : time.contacted();
                        int visibleCount = resting.count()+(inHand ? full.count() : 0);
                        require(visibleCount == size,"prop lost or duplicated at contact: "+item);
                        samples++;
                    }
                    require(time.done() && time.contacted(),"valid transfer failed to finish");
                    // A refused prediction restores the real slot, and stale acks cannot resurrect its prop.
                    var correction = new TransferDisplay<>(before,after,Stack::equals);
                    require(!correction.reconcile(before),"correction not detected");
                    require(correction.resting(before,false).equals(before),"correction hid actual state");
                    require(!correction.reconcile(after),"late ack resurrected cancelled transfer");
                }
        // Merging/taking part of a stack keeps the existing remainder while the transferred batch moves.
        for (boolean insert : new boolean[]{true,false}) {
            Stack low = new Stack("cyan",16,""), high = new Stack("cyan",64,"");
            var display = new TransferDisplay<>(insert ? low : high,insert ? high : low,Stack::equals);
            require(display.resting(insert ? high : low,false).count() == (insert ? 16 : 64),"pre-contact count");
            require(display.resting(insert ? high : low,true).count() == (insert ? 64 : 16),"post-contact count");
            require(!display.reconcile(new Stack("cyan",15,"")),"consumption/correction must release stale presentation");
        }
        var differentFilm = new TransferDisplay<>(EMPTY,new Stack("film",1,"frames-A"),Stack::equals);
        require(!differentFilm.reconcile(new Stack("film",1,"frames-B")),"component-only correction ignored");
        var unreachable = new TransferTimeline(1,.7);
        for (int i=0;i<200;i++) unreachable.advance(.01);
        require(unreachable.done() && !unreachable.contacted(),"unreachable hand strands animation");

        // Abrupt target changes inherit position AND velocity; acknowledgement never owns the spring.
        Point origin = new Point(0,0,0), shoulder = new Point(0,-.2,0);
        for (int fps : new int[]{30,60,144}) {
            var hand = new HandSpring(origin);
            for (int frame=0;frame<fps*4;frame++) {
                Point target = frame < fps ? new Point(.4,.2,.3) : frame < fps*2 ? new Point(-.3,.1,.4) : origin;
                Point p = hand.position(), v = hand.velocity();
                hand.update(target,0);
                near(hand.position(),p,"action handoff teleported position");
                near(hand.velocity(),v,"action handoff cleared velocity");
                hand.update(target,1.0/fps);
                Point next = hand.limit(shoulder,.74);
                require(next.subtract(shoulder).length() <= .740000001,"reach limit");
                require(next.subtract(p).length() < .14,"pose discontinuity");
            }
        }
        // The host derives palm roll from world-up; crossing its pole must not turn a sleeve inside out.
        for (double side : new double[]{0,.00001,.01,.1}) {
            var arm = new ArmContinuity();
            Point previous = null;
            for (int i=0;i<=2000;i++) {
                double angle = (i-1000)*.0007;
                Point d = new Point(Math.sin(angle),-Math.cos(angle),side);
                d = d.scale(1/d.length());
                Point up = arm.update(d,new Point(0,0,1),1.0/144);
                require(Math.abs(up.length()-1)<1e-9 && Math.abs(up.dot(d))<1e-9,"invalid palm basis");
                if (previous != null) require(up.dot(previous)>.99,"palm flipped at vertical pose");
                previous = up;
            }
        }
        System.out.println("PASS: "+samples+" transfer/ack samples, correction, stack merge, timeout, interrupted spring and palm-roll continuity");
    }
}
