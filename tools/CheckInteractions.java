import io.github.donquijotee.cinematic_exposure.client.CameraAssembly.Point;
import io.github.donquijotee.cinematic_exposure.client.HandExchange;
import io.github.donquijotee.cinematic_exposure.client.InteractionMotion;

/** Pure controller checks. Does not load Minecraft, a menu, or any rendering classes. */
class CheckInteractions {
    static void require(boolean b, String reason) { if (!b) throw new AssertionError(reason); }
    static void near(Point a, Point b, String reason) { require(a.subtract(b).length()<1e-9,reason); }
    public static void main(String[] args) {
        // A deliberate crossing changes hands once; jitter inside the dead band never changes ownership.
        for (boolean owner : new boolean[]{true,false}) {
            boolean hand = owner;
            for (int i=0;i<1000;i++) hand = InteractionMotion.chooseRight(hand,.13*Math.sin(i));
            require(hand == owner,"centre jitter changed hands");
            require(!InteractionMotion.chooseRight(hand,-.3),"left crossing");
            require(InteractionMotion.chooseRight(hand,.3),"right crossing");
        }
        double previous = InteractionMotion.softBound(-2,-.43,.43,.09);
        for (int i=1;i<=40000;i++) {
            double x = -2+i*.0001, next = InteractionMotion.softBound(x,-.43,.43,.09);
            require(next >= -.43 && next <= .43,"outside work surface");
            require(next >= previous-1e-12 && next-previous <= .000100001,"edge snap or reversed movement");
            previous = next;
        }
        Point axis = new Point(1,0,0), meeting = new Point(0,.2,0);
        Point homeR = new Point(.29,.07,.1), homeL = new Point(-.29,.07,.1);
        int samples = 0;
        for (boolean owner : new boolean[]{true,false}) for (int fps : new int[]{30,60,144})
            for (double speed : new double[]{.25,1,3}) {
                HandExchange exchange = new HandExchange();
                exchange.reset(owner);
                exchange.begin(!owner,homeR,homeL,meeting,axis);
                near(exchange.targets(homeR,homeL)[0],homeR,"right start jump");
                near(exchange.targets(homeR,homeL)[1],homeL,"left start jump");
                Point r = homeR, l = homeL;
                Point previousProp = exchange.itemPoint(r,l);
                double dt = speed/fps;
                for (int i=0;exchange.active() && i<1000;i++) {
                    // An opposite request while exchanging must not reverse ownership mid-transfer.
                    exchange.begin(owner,r,l,meeting,axis);
                    require(exchange.destinationRight() == !owner,"handoff reversed mid-flight");
                    exchange.advance(dt);
                    Point[] goals = exchange.targets(homeR,homeL);
                    double k = 1-Math.exp(-20*dt);
                    r = r.lerp(goals[0],k); l = l.lerp(goals[1],k);
                    Point prop = exchange.itemPoint(r,l);
                    // Prop stays on the actual smoothed hand-to-hand segment, including at ownership change.
                    require(Math.abs(prop.subtract(r).length()+prop.subtract(l).length()-r.subtract(l).length()) < 1e-8,
                            "prop detached from smoothed hands");
                    require(prop.subtract(previousProp).length() < 3*dt+.001,"prop teleported");
                    previousProp = prop; samples++;
                }
                require(!exchange.active() && exchange.right() == !owner,"handoff did not complete");
                near(exchange.itemPoint(r,l),owner ? l : r,"prop not owned by destination hand");
                near(exchange.targets(homeR,homeL)[0],homeR,"right end discontinuity");
                near(exchange.targets(homeR,homeL)[1],homeL,"left end discontinuity");
                exchange.begin(owner,r,l,meeting,axis);
                exchange.advance(.24);
                exchange.reset(exchange.right());
                require(!exchange.active(),"cancel did not clear handoff");
                require(Double.isFinite(exchange.itemPoint(r,l).length()),"cancel left invalid item position");
            }
        System.out.println("PASS: cursor bounds, hand-selection hysteresis, "+samples+" handoff samples and interruption");
    }
}
