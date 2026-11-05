package io.github.donquijotee.cinematic_exposure.client;

import dev.dyslexic.cinematic.client.CameraRig;
import dev.dyslexic.cinematic.client.Scene;
import io.github.donquijotee.cinematic_exposure.ClientConfig;
import io.github.mortuusars.exposure.world.inventory.CameraInHandAttachmentsMenu;
import io.github.mortuusars.exposure.world.item.camera.Attachment;
import io.github.mortuusars.exposure.world.item.camera.CameraItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.ItemStack;
import java.util.List;

/** Camera presentation only. Exposure owns every click, stack, attachment and synchronized value. */
public final class VisualSession {
    private static VisualSession current;
    private static long lastNanos;
    private static Object world;
    private final CameraInHandAttachmentsMenu menu;
    private List<ItemStack> clickBefore;
    private SlotTransfer transfer;
    public Motion motion;
    private CameraAction action;
    private ItemStack clickCarried = ItemStack.EMPTY;
    private boolean clickedWithHeldItem;
    private boolean leaving;
    private double weight, fromWeight, transitionTime, frameStep;
    private final Vec3 originEye;
    public final Vec3 forward, right;
    public Vec3 work;
    private final Vec3 desiredEye;
    private final float desiredYaw;
    public final BlockPos lightPos;
    private ItemStack cameraStack = ItemStack.EMPTY;
    private HandSpring rightHand, leftHand;
    final ArmContinuity rightArm = new ArmContinuity(), leftArm = new ArmContinuity();
    private Vec3 cursorTarget;
    private boolean cursorRight;

    private VisualSession(CameraInHandAttachmentsMenu menu) {
        Minecraft mc = Minecraft.getInstance();
        this.menu = menu;
        originEye = mc.player.getEyePosition();
        desiredYaw = mc.player.getYRot();
        forward = direction(desiredYaw);
        right = new Vec3(-forward.z, 0, forward.x);
        work = ((CameraSceneScreen) mc.screen).workPoint(originEye,forward,right);
        desiredEye = originEye.add(forward.scale(0.045)).add(0,-0.03,0);
        lightPos = BlockPos.containing(originEye);
        refresh();
    }

    public static boolean supports(Screen screen) {
        var player = Minecraft.getInstance().player;
        return player != null && screen instanceof CameraSceneScreen c && ClientConfig.CAMERA.get()
                && c.getMenu() == player.containerMenu && c.getMenu().getCamera().getStack().getItem() instanceof CameraItem;
    }
    public static VisualSession get() { return current; }

    public static void frame() {
        Minecraft mc = Minecraft.getInstance();
        long now = System.nanoTime();
        double dt = lastNanos == 0 ? 0 : Math.min(0.1,(now-lastNanos)/1.0e9);
        lastNanos = now;
        if (world != mc.level || mc.player == null || mc.level == null || !mc.player.isAlive()
                || mc.getCameraEntity() != mc.player || !mc.options.getCameraType().isFirstPerson() || Scene.active()) {
            current = null; world = mc.level; return;
        }
        if (mc.isPaused()) return;
        if (mc.screen != null && !(mc.screen instanceof CameraSceneScreen)) { current = null; return; }
        boolean supported = supports(mc.screen);
        if (current != null && supported && current.menu != mc.player.containerMenu) current = null;
        if (current == null && supported) current = new VisualSession((CameraInHandAttachmentsMenu) mc.player.containerMenu);
        if (current == null) return;
        VisualSession s = current;
        if (mc.screen instanceof CameraSceneScreen screen) {
            Vec3 next = screen.workPoint(s.originEye,s.forward,s.right);
            if (s.work.distanceToSqr(next) > 1e-8) {
                // Invalidate the old socket path, not the hands or their spring velocity.
                s.cancelTransfer();
                s.work = next;
            }
        }
        boolean present = supported && mc.player.containerMenu == s.menu && s.originEye.distanceTo(mc.player.getEyePosition()) < 2;
        if (!present && !s.leaving) {
            s.leaving = true; s.fromWeight = s.weight; s.transitionTime = 0; s.cancelTransfer();
        } else if (present && s.leaving) {
            s.leaving = false; s.fromWeight = s.weight; s.transitionTime = 0;
        }
        double step = dt * ClientConfig.SPEED.get();
        s.frameStep = step;
        s.transitionTime += step;
        s.weight = Mth.lerp(Motion.ease(s.transitionTime/0.65),s.fromWeight,s.leaving ? 0 : 1);
        if (!s.leaving) {
            s.refresh();
            if (s.motion != null) {
                s.motion.advance(step);
                if (s.motion.done()) s.cancelTransfer();
            }
        }
        if (s.leaving && s.weight < 0.001) current = null;
    }

    /** Captures a real vanilla click, including QUICK_MOVE, drag and number-key swaps. */
    public static void beforeClick(int containerId) {
        if (!observes(containerId)) return;
        current.refresh();
        current.clickCarried = current.menu.getCarried().copy();
        current.clickedWithHeldItem = Minecraft.getInstance().screen instanceof CameraSceneScreen screen
                && !screen.cursorInDrawer() && !Minecraft.getInstance().options.touchscreen().get();
        current.clickBefore = java.util.stream.IntStream.range(0,current.attachments().size())
                .mapToObj(i -> current.menu.getSlot(i).getItem().copy()).toList();
    }
    public static void afterClick(int containerId) {
        VisualSession s = current;
        if (!observes(containerId) || s.clickBefore == null) return;
        List<ItemStack> before = s.clickBefore;
        s.clickBefore = null;
        for (int i=0;i<before.size();i++) {
            ItemStack after = s.menu.getSlot(i).getItem();
            if (ItemStack.matches(before.get(i),after)) continue;
            // Native-style takes are immediate. Only a real insertion needs a placement gesture.
            if (SlotTransfer.inserting(before.get(i),after)) s.startTransfer(i,before.get(i),after);
        }
        s.cameraStack = s.menu.getCamera().getStack().copy();
    }

    private static boolean observes(int containerId) {
        return current != null && !current.leaving && current.menu.containerId == containerId
                && supports(Minecraft.getInstance().screen) && Minecraft.getInstance().player.containerMenu == current.menu;
    }

    /** A confirmation is not a new operation. A genuine correction releases only the stale prop. */
    public static void observe() { if (current != null && !current.leaving) current.refresh(); }
    public static void resynchronize(int containerId) {
        if (current != null && current.menu.containerId == containerId) current.refresh();
    }
    private void refresh() {
        if (!(menu.getCamera().getStack().getItem() instanceof CameraItem)) { cancelTransfer(); return; }
        cameraStack = menu.getCamera().getStack().copy();
        if (transfer != null && !transfer.reconcile(menu.getSlot(transfer.slot).getItem())) cancelTransfer();
    }
    private void cancelTransfer() {
        transfer = null; motion = null; action = null;
    }
    private List<Attachment<?>> attachments() { return menu.getCamera().map((item,stack) -> item.getAttachments()); }
    private static Motion.Kind kind(Attachment<?> attachment) {
        if (attachment.equals(Attachment.FILM)) return Motion.Kind.FILM;
        if (attachment.equals(Attachment.FLASH)) return Motion.Kind.FLASH;
        if (attachment.equals(Attachment.LENS)) return Motion.Kind.LENS;
        return Motion.Kind.FILTER;
    }

    private void startTransfer(int slot, ItemStack before, ItemStack after) {
        ItemStack item = SlotTransfer.moved(before,after,true);
        Motion.Kind kind = kind(attachments().get(slot));
        var part = CameraAssembly.Part.valueOf(kind.name());
        boolean right = kind != Motion.Kind.LENS && kind != Motion.Kind.FILTER;
        boolean holding = clickedWithHeldItem && ItemStack.isSameItemSameComponents(clickCarried,item);
        motion = new Motion(kind,true,item,part.duration,CameraAssembly.CONTACT);
        transfer = new SlotTransfer(slot,before,after,motion,false);
        var outside = switch (kind) {
            case FILM -> CameraAssembly.FILM_OUTSIDE;
            case FLASH -> CameraAssembly.FLASH_OUTSIDE;
            default -> CameraAssembly.LENS_SOCKET.add(new CameraAssembly.Point(0,0,kind == Motion.Kind.FILTER ? -.12 : -.22));
        };
        // A replacement removes the old prop immediately, then places the accepted new item once.
        // The starting fist and its spring velocity survive every input and server acknowledgement.
        action = new CameraAction(motion,new CameraAssembly.Placement(part,point(position(right)),point(rest(right)),
                point(local(right ? CameraAssembly.RIGHT_TAKE : CameraAssembly.LEFT_TAKE)),point(socket(kind)),
                point(local(outside)),point(local(CameraAssembly.FILM_GATE)),holding));
    }

    public boolean busy() { return motion != null && !motion.done(); }
    public static boolean blocksClick(int containerId) {
        return observes(containerId) && current.busy()
                && Minecraft.getInstance().screen instanceof CameraSceneScreen screen && !screen.finishingInput();
    }

    public ItemStack cameraStack() {
        ItemStack display = cameraStack.copy();
        if (motion == null) return display;
        Attachment<?> attachment = attachments().get(transfer.slot);
        // Both renders use the same contact decision, made from this frame's actual smoothed fist.
        if (!motion.contacted()) display.remove(attachment.component());
        return display;
    }
    public CameraAction action() { return action; }
    public void contact(Vec3 hand) { if (motion != null) motion.touch(hand.distanceToSqr(socket(motion.kind)) < .045*.045); }

    /** World cursor carrying, bounded in camera-local space so it cannot sweep through the body. */
    public void updatePointer() {
        cursorTarget = null;
        var mc = Minecraft.getInstance();
        if (leaving || weight < .99 || !(mc.screen instanceof CameraSceneScreen screen) || screen.cursorInDrawer()) return;
        boolean carrying = !menu.getCarried().isEmpty();
        int hovered = screen.hoveredAttachment();
        if (!carrying && hovered < 0) return;
        Motion.Kind hoverKind = hovered < 0 ? null : screen.kind(hovered);
        cursorRight = carrying ? carriedRight() : hoverKind != Motion.Kind.LENS && hoverKind != Motion.Kind.FILTER;
        Vec3 hit = dev.dyslexic.cinematic.client.Picking.cursorOnPlane(carry(cursorRight),forward);
        if (hit == null) return;
        Vec3 offset = hit.subtract(work);
        var point = CameraAssembly.unorient(new CameraAssembly.Point(offset.dot(right),offset.y,offset.dot(forward)));
        Vec3 target = local(CameraAssembly.freeTarget(point,cursorRight));
        boolean sameHand = hoverKind != null && cursorRight == (hoverKind != Motion.Kind.LENS && hoverKind != Motion.Kind.FILTER);
        if (sameHand && (!carrying || menu.getSlot(hovered).mayPlace(menu.getCarried()))) {
            var approach = switch (hoverKind) {
                case FILM -> CameraAssembly.FILM_GATE;
                case FLASH -> new CameraAssembly.Point(.09,.12,.42);
                default -> CameraAssembly.LENS_SOCKET.add(new CameraAssembly.Point(0,0,-.22));
            };
            Vec3 magnet = local(CameraAssembly.freeTarget(approach,cursorRight));
            target = target.lerp(magnet,InteractionMotion.attraction(target.distanceTo(magnet),.23));
        }
        var view = mc.gameRenderer.getMainCamera();
        cursorTarget = HandReach.fromAnchor(carry(cursorRight),target,HandReach.shoulder(view.getPosition(),direction(view.getYRot()),cursorRight));
    }
    public Vec3 followTarget(boolean right) {
        if (cursorTarget != null && cursorRight == right) return cursorTarget;
        return !menu.getCarried().isEmpty() && carriedRight() == right ? carry(right) : rest(right);
    }
    public Vec3 actionTarget(CameraAction action) {
        // Only the final return follows the live cursor; insertion keeps its safe socket corridor.
        double start = motion.kind == Motion.Kind.FILM ? .92 : .90;
        return action.hand().lerp(followTarget(motionRight()),Motion.window(motion.progress(),start,1));
    }
    public Vec3 entranceOffset() { return new Vec3(0,-1.25*(1-weight),0).add(forward.scale(-.14*(1-weight))); }
    private Vec3 position(boolean right) { return rightHand != null ? vector((right ? rightHand : leftHand).position()) : rest(right); }
    public Vec3 smooth(boolean right, Vec3 target) {
        if (rightHand == null) {
            leftHand = new HandSpring(point(rest(false))); rightHand = new HandSpring(point(rest(true)));
        }
        var view = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 shoulder = HandReach.shoulder(view.getPosition(),direction(view.getYRot()),right);
        HandSpring hand = right ? rightHand : leftHand;
        hand.update(point(HandReach.limit(target,shoulder)),frameStep);
        // Retain the spring state across input, confirmation, contact, exit and GUI resize.
        return vector(hand.limit(point(shoulder),HandReach.MAX));
    }
    private static CameraAssembly.Point point(Vec3 v) { return new CameraAssembly.Point(v.x,v.y,v.z); }
    private static Vec3 vector(CameraAssembly.Point p) { return new Vec3(p.x(),p.y(),p.z()); }
    public double frameStep() { return frameStep; }
    public double weight() { return weight; }
    public Vec3 local(double x, double y, double z) { return work.add(assemblyOffset(new CameraAssembly.Point(x,y,z),forward,right)); }
    public Vec3 local(CameraAssembly.Point point) { return local(point.x(),point.y(),point.z()); }
    public static Vec3 assemblyOffset(CameraAssembly.Point point, Vec3 forward, Vec3 right) {
        var tilted = CameraAssembly.orient(point);
        return right.scale(tilted.x()).add(0,tilted.y(),0).add(forward.scale(tilted.z()));
    }
    public Vec3 rest(boolean right) { return local(right ? CameraAssembly.RIGHT_REST : CameraAssembly.LEFT_GRIP); }
    public Vec3 carry(boolean right) { return local(right ? CameraAssembly.RIGHT_CARRY : CameraAssembly.LEFT_CARRY); }
    public Vec3 socket(Motion.Kind kind) {
        return local(switch (kind) {
            case FILM -> CameraAssembly.FILM_SOCKET;
            case FLASH -> CameraAssembly.FLASH_SOCKET;
            default -> CameraAssembly.LENS_SOCKET;
        });
    }
    public boolean motionRight() { return motion == null || motion.kind != Motion.Kind.LENS && motion.kind != Motion.Kind.FILTER; }
    public boolean carriedRight() {
        ItemStack carried = menu.getCarried();
        return !Attachment.LENS.matches(carried) && !Attachment.FILTER.matches(carried);
    }
    public static CameraRig.Pose camera(Vec3 live, float yaw, float pitch) {
        VisualSession s = current;
        Minecraft mc = Minecraft.getInstance();
        if (s == null || Scene.active() || mc.level == null || mc.player == null || !mc.options.getCameraType().isFirstPerson()) return null;
        Vec3 target = s.desiredEye, delta = target.subtract(live);
        if (delta.length() > .35) target = live.add(delta.normalize().scale(.35));
        var hit = mc.level.clip(new ClipContext(live,target,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mc.player));
        if (hit.getType() != HitResult.Type.MISS) {
            Vec3 travel = hit.getLocation().subtract(live);
            target = travel.length() > .06 ? live.add(travel.normalize().scale(travel.length()-.06)) : live;
        }
        float k = (float)s.weight;
        return new CameraRig.Pose(live.lerp(target,k),yaw+Mth.wrapDegrees(s.desiredYaw-yaw)*k,Mth.lerp(k,pitch,18),0);
    }
    public static Vec3 direction(float yaw) {
        double radians = Math.toRadians(yaw);
        return new Vec3(-Math.sin(radians),0,Math.cos(radians));
    }
}
