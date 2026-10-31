import io.github.donquijotee.cinematic_exposure.client.CameraAssembly.Point;
import io.github.donquijotee.cinematic_exposure.client.TablePlacement;
import io.github.donquijotee.cinematic_exposure.client.TransferDisplay;
import io.github.donquijotee.cinematic_exposure.client.TransferTimeline;

/** Pure placement geometry and event-order checks. No Minecraft classes or game bootstrap. */
class CheckTablePlacement {
    static void require(boolean condition, String why) { if (!condition) throw new AssertionError(why); }
    static void near(Point a, Point b, String why) { require(a.subtract(b).length()<1e-8,why); }
    static Point turn(Point p, int quarter) {
        return switch (quarter) {
            case 1 -> new Point(-p.z(),p.y(),p.x());
            case 2 -> new Point(-p.x(),p.y(),-p.z());
            case 3 -> new Point(p.z(),p.y(),-p.x());
            default -> p;
        };
    }
    // Reference integration of the inspected host Anim.Spring3 equation, not a loaded game class.
    static final class HostSpring {
        Point at, velocity = new Point(0,0,0);
        HostSpring(Point at) { this.at = at; }
        void step(Point target, double dt) {
            double omega = 20, e = Math.exp(-omega*dt);
            Point d = at.subtract(target), travel = velocity.add(d.scale(omega)).scale(dt);
            velocity = velocity.subtract(travel.scale(omega)).scale(e);
            at = target.add(d.add(travel).scale(e));
        }
    }
    public static void main(String[] args) {
        int samples = 0, placements = 0;
        double longest = 0;
        for (int quarter=0;quarter<4;quarter++) for (int slot=0;slot<6;slot++) {
            double x = slot==0 ? -.36 : (slot-3)*.16, z = slot==0 ? 0 : .35;
            boolean right = x>=0;
            Point cell = turn(new Point(x,0,z),quarter);
            Point rest = turn(new Point(right ? .29 : -.29,.07,.36),quarter);
            Point side = turn(new Point(0,0,1),quarter);
            Point take = TablePlacement.take(rest,side);
            Point shoulder = turn(new Point(right ? .38 : -.38,.16,.62),quarter);
            Point toward = cell.subtract(new Point(rest.x(),0,rest.z()));
            toward = toward.scale(1/toward.length());
            Point offset = toward.scale(TablePlacement.ITEM_REACH).add(new Point(0,-TablePlacement.HAND_Y+.012,0));
            Point grip = cell.subtract(offset);
            require(Math.abs(take.dot(side)-.50)<1e-9 && Math.abs(take.y()-.10)<1e-9,"retrieval not at table edge");
            near(grip.add(offset),cell,"prop/socket mismatch");
            for (boolean held : new boolean[]{false,true}) {
                Point start = held ? grip.add(new Point(0,.08,0)) : rest;
                var path = new TablePlacement.Path(start,rest,take,grip,held);
                near(path.hand(0),start,"placement start jump");
                near(path.hand(1),rest,"placement end jump");
                near(path.hand(TablePlacement.DROP),grip,"placement misses socket");
                if (!held) {
                    near(path.hand(.28),take,"Shift placement does not pass table edge");
                    require(path.itemScale(.239)==0,"item revealed before table-edge pickup");
                }
                for (int i=0;i<=1000;i++) {
                    Point p = path.hand(i/1000.0);
                    require(p.y()>=.0499,"path dips below tabletop");
                    require(p.subtract(shoulder).length()<=.74,"authored hand exceeds reach");
                }
                for (double boundary : new double[]{.24,.28,.58,.64,.70})
                    require(path.hand(boundary-1e-7).subtract(path.hand(boundary+1e-7)).length()<1e-5,"placement phase jump");
                for (int fps : new int[]{30,60,144}) for (double speed : new double[]{.25,1,3}) {
                    var time = new TransferTimeline(TablePlacement.DURATION,TablePlacement.DROP);
                    var display = new TransferDisplay<>("old film","new film",String::equals);
                    var hand = new HostSpring(start);
                    double dt = speed/fps, total = 0;
                    for (int frame=0;!time.done() && frame<2000;frame++) {
                        time.advance(dt); total+=dt;
                        hand.step(path.hand(time.progress()),dt);
                        time.touch(hand.at.subtract(grip).length()<.035);
                        // The new film is confirmed before the hand arrives, often by a full menu packet.
                        require(display.reconcile("new film"),"confirmation cancelled placement");
                        boolean hide = TablePlacement.hidesFilm(true,slot,time.contacted(),time.done());
                        if (slot == 0) require(hide == display.resting("new film",time.contacted()||time.done()).equals("old film"),
                                "preview and resting film disagree");
                        else require(!hide,"paper/dye placement hides an existing film preview");
                        if (!time.done()) require(TablePlacement.busy(true,false),"placement unlocked before release finished");
                        if (time.contacted() && !time.done()) {
                            require(TablePlacement.busy(true,false),"contact unlocked input during return");
                            require(!hide,"contact did not reveal film preview");
                        }
                        samples++;
                    }
                    require(time.contacted() && time.done(),"hand never settled on slot");
                    require(!TablePlacement.busy(true,time.done()),"input stays locked after return");
                    longest=Math.max(longest,total); placements++;
                }
            }
        }
        // Esc/correction destroys the local placement. The real stack is visible without a stale lock/preview flag.
        require(!TablePlacement.busy(false,false),"cancel leaves input locked");
        require(!TablePlacement.hidesFilm(false,0,false,false),"cancel leaves film hidden");
        var rejected = new TransferDisplay<>("empty","film",String::equals);
        require(!rejected.reconcile("empty"),"prediction correction ignored");
        require(rejected.resting("empty",false).equals("empty"),"rejected film persists");
        System.out.println("PASS: "+placements+" table placements / "+samples+" frames; four facings, edge origin, contact, preview and input release; longest="+longest+" animation seconds");
    }
}
