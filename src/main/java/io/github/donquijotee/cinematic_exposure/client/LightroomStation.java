package io.github.donquijotee.cinematic_exposure.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.dyslexic.cinematic.api.client.AddonMenuStation;
import dev.dyslexic.cinematic.client.*;
import io.github.donquijotee.cinematic_exposure.ClientConfig;
import io.github.donquijotee.cinematic_exposure.mixin.HandsInvoker;
import io.github.mortuusars.exposure.world.inventory.LightroomMenu;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/** The same approach/lean/menu/hands lifecycle used by the host's vanilla workstations. */
public final class LightroomStation extends AddonMenuStation<LightroomMenu> {
    private final Vec3 top;
    private int hovered = -1;
    private ItemStack result = ItemStack.EMPTY;
    private List<ItemStack> clickBefore;
    private SlotTransfer transfer;
    private Motion printMotion;
    private Vec3 printRight, printLeft;
    private boolean initialized, printing;
    private Motion motion;
    private double lastFrame;
    private LightroomStatus status;
    private boolean overPanel;
    private boolean motionFromHand, motionRight = true, waitingForExchange;
    private final HandExchange exchange = new HandExchange();
    private ItemStack carriedSnapshot = ItemStack.EMPTY;
    private Vec3 motionStart;
    private final HandPickup pickup = new HandPickup();
    private double tappedAt = -10;
    private static final String[] LABELS = {"film", "paper", "cyan", "magenta", "yellow", "black", "photo",
        "print", "previous", "next", "process"};
    public LightroomStation(BlockPos pos, Direction side) {
        super(pos, side, LightroomMenu.class, 0); // Lightroom retains inputs; never return them on exit.
        top = LightroomLayout.top(pos);
    }
    @Override public Vec3 center() { return top; }
    @Override public Vec3 workPoint() { return top; }
    @Override public boolean worksAtAnAngle() { return true; }
    @Override public List<Vec3> approach(LocalPlayer player) {
        Vec3 feet = top.add(side().scale(0.86));
        return List.of(new Vec3(feet.x, player.getY(), feet.z));
    }
    @Override public CameraRig.Pose lean() {
        return new CameraRig.Pose(top.add(side().scale(0.70)).add(0, 0.62, 0), side.getOpposite().toYRot(), 40, 0);
    }
    @Override public Vec3 rest(boolean rightHand) {
        return top.add(side().scale(0.36)).add(right().scale(rightHand ? 0.29 : -0.29)).add(0, 0.07, 0);
    }
    private Vec3 point(int i) {
        return LightroomLayout.point(pos, side, i);
    }
    @Override protected Vec3 restPoint(int slot) { return point(slot); }
    @Override public void updateCursor(boolean overPanel) {
        this.overPanel = overPanel;
        hovered=-1; aim=null;
        if (overPanel || menu()==null) return;
        Vec3 hit=Picking.cursorOnPlane(top.y);
        if(hit==null) return;
        double best=0.105*0.105;
        for(int i=0;i<11;i++) {
            double distance=point(i).distanceToSqr(hit);
            if(distance<best) { best=distance; hovered=i; }
        }
        Vec3 relative = hit.subtract(top);
        if (hovered < 0 && Math.abs(relative.dot(right())) <= 0.22 && Math.abs(relative.dot(side())) <= 0.22
                && menu().getFrameByIndex(menu().getSelectedFrame()) != null) hovered = 7;
        double lateral = relative.dot(right()), depth = relative.dot(side());
        // Picking/clicks still use original slots; motion also follows empty space between them.
        if (Math.abs(lateral) <= 0.72 && Math.abs(depth) <= 0.72) {
            double x = InteractionMotion.softBound(lateral,-0.43,0.43,0.09);
            double z = InteractionMotion.softBound(depth,-0.28,0.43,0.10);
            aim = top.add(right().scale(x)).add(side().scale(z));
            int magnet = -1;
            double distance = 0.18;
            for (int i=0;i<7;i++) if ((menu().getCarried().isEmpty() || menu().getSlot(i).mayPlace(menu().getCarried()))
                    && hit.distanceTo(point(i)) < distance) {
                magnet = i; distance = hit.distanceTo(point(i));
            }
            if (magnet >= 0) aim = aim.lerp(point(magnet),InteractionMotion.attraction(distance,0.18));
            // Rear labels are clickable without pulling the sleeve across the entire workbench.
            if (hovered >= 7 && hit.distanceToSqr(point(hovered)) < 0.105*0.105)
                aim = top.add(right().scale(lateral < 0 ? -0.27 : 0.27)).add(side().scale(0.14));
        }
    }
    @Override public Vec3 handTarget(Vec3 aim, Vec3 shoulder, boolean carrying, double tap) {
        return aim.add(0, (carrying?0.12:0.07)-0.035*tap, 0);
    }
    @Override public double maxReach() { return HandReach.MAX; }
    @Override public double handsStiffness() { return 20 * ClientConfig.SPEED.get(); }
    @Override public Vec3 heldItemPoint(Vec3 hand, Vec3 shoulder) { return hand.add(0,-0.055,0); }
    @Override public void quickMove(int inventorySlot, int menuSlot) {
        var mc = Minecraft.getInstance();
        if (menu() == null || mc.player == null || Scene.phase != Scene.Phase.ACTIVE) return;
        // The host's default QUICK is server-only. Delegate once to Exposure's ordinary menu
        // click instead, giving its normal prediction and our before/after observer one boundary.
        if (menuSlot < 7 || menuSlot >= menu().slots.size()
                || menu().getSlot(menuSlot).container != mc.player.getInventory()
                || menu().getSlot(menuSlot).getContainerSlot() != inventorySlot) {
            menuSlot = -1;
            for (int i=7;i<menu().slots.size();i++) if (menu().getSlot(i).container == mc.player.getInventory()
                    && menu().getSlot(i).getContainerSlot() == inventorySlot) { menuSlot = i; break; }
        }
        if (menuSlot >= 7) slotClick(menuSlot,0,true);
    }
    @Override protected void act(int button, boolean shift) {
        if(menu()==null || hovered<0 || button>1) return;
        if (hovered == 10 && !menu().canChangeProcess()) return;
        tappedAt = Scene.clock();
        if(hovered<7) { slotClick(hovered,button,shift); return; }
        int action=switch(hovered) {
            case 7 -> shift && Minecraft.getInstance().player.isCreative()?LightroomMenu.PRINT_CREATIVE_BUTTON_ID:LightroomMenu.PRINT_BUTTON_ID;
            case 8 -> LightroomMenu.PREVIOUS_FRAME_BUTTON_ID;
            case 9 -> LightroomMenu.NEXT_FRAME_BUTTON_ID;
            default -> LightroomMenu.TOGGLE_PROCESS_BUTTON_ID;
        };
        // Original button packet and original server checks remain authoritative.
        Minecraft.getInstance().gameMode.handleInventoryButtonClick(menu().containerId,action);
    }
    private int count(ItemStack stack) {
        return stack.getItem() instanceof io.github.mortuusars.exposure.world.item.StackedPhotographsItem photos
            ? photos.getPhotographs(stack).size() : stack.getCount();
    }
    @Override public void tick() {
        if(menu()==null) return;
        status = LightroomStatus.read(menu().getBlockEntity());
        if(!ClientConfig.LIGHTROOM.get()) Scene.leave();
    }
    /** Capture only direct player transfers: server printing consumption is deliberately not a pickup. */
    public void beforeClick(int containerId) {
        if (menu() == null || menu().containerId != containerId || Scene.phase != Scene.Phase.ACTIVE) return;
        synchronize(containerId);
        clickBefore = java.util.stream.IntStream.range(0,7).mapToObj(i -> slot(i).copy()).toList();
    }
    public void afterClick(int containerId) {
        if (menu() == null || menu().containerId != containerId || clickBefore == null) return;
        var before = clickBefore;
        clickBefore = null;
        for (int i=0;i<7;i++) {
            ItemStack was = before.get(i), now = slot(i);
            if (ItemStack.matches(was,now)) continue;
            boolean inserting = SlotTransfer.inserting(was,now);
            // A photo stack's count lives in its component, rather than ItemStack.count.
            if (i == 6) {
                if (count(now) >= count(was)) continue;
                inserting = false;
            }
            ItemStack moved = SlotTransfer.moved(was,now,inserting);
            if (i == 6 && was.getItem() instanceof io.github.mortuusars.exposure.world.item.StackedPhotographsItem photos
                    && count(was)-count(now) == 1) moved = photos.getPhotographs(was).getItemUnsafe(0).copy();
            Motion.Kind kind = i == 0 ? Motion.Kind.FILM : i == 6 ? Motion.Kind.PHOTO : Motion.Kind.MATERIAL;
            boolean bag = !inserting && !ItemStack.isSameItemSameComponents(menu().getCarried(),moved);
            Motion next = new Motion(kind,inserting,moved,inserting ? 1.0 : .95,inserting ? .70 : .26);
            beginMotion(new SlotTransfer(i,was,now,next,bag));
        }
    }
    public void synchronize(int containerId) {
        if (menu() == null || menu().containerId != containerId) return;
        if (transfer != null && !transfer.reconcile(slot(transfer.slot))) cancelTransfer();
    }
    private void observePrinting() {
        if (menu() == null) return;
        synchronize(menu().containerId);
        ItemStack nextResult = slot(6);
        boolean active = menu().isPrinting();
        if (initialized && !printing && (active || count(nextResult) > count(result))) {
            printMotion = new Motion(Motion.Kind.PRINT,true,ItemStack.EMPTY);
            printRight = HandsInvoker.cinematicExposure$position(true);
            printLeft = HandsInvoker.cinematicExposure$position(false);
        }
        if (printing && !active) printMotion = null;
        initialized = true; result = nextResult.copy(); printing = active;
    }
    private void cancelTransfer() {
        motion = null; transfer = null; waitingForExchange = false;
        exchange.reset(exchange.right());
    }
    private void beginMotion(SlotTransfer next) {
        boolean rightHand = next.slot == 0 ? false : next.slot == 6 || lateral(point(next.slot)) >= 0;
        boolean held = pickup.holds(next.motion.item) || showsMotionItem() && motionRight == rightHand
                && ItemStack.isSameItemSameComponents(motion.item,next.motion.item);
        motionFromHand = next.motion.inserting && held;
        motionRight = rightHand;
        motion = next.motion;
        transfer = next;
        if (motionFromHand) {
            // A click during a handover must not teleport its prop to the nominal owner.
            // Finish the existing hand-to-hand segment, then approach the clicked slot.
            if (!exchange.active() && exchange.right() != motionRight) requestExchange(motionRight);
            waitingForExchange = exchange.active();
        } else {
            exchange.reset(motionRight);
            waitingForExchange = false;
        }
        motionStart = HandsInvoker.cinematicExposure$position(motionRight);
    }
    private void requestExchange(boolean rightHand) {
        // Meet above the near edge of the table, keeping both sleeves short and off the photograph.
        exchange.begin(rightHand,asPoint(HandsInvoker.cinematicExposure$position(true)),
                asPoint(HandsInvoker.cinematicExposure$position(false)),asPoint(top.add(side().scale(0.34)).add(0,0.20,0)),asPoint(right()));
    }
    private static CameraAssembly.Point asPoint(Vec3 v) { return new CameraAssembly.Point(v.x,v.y,v.z); }
    private static Vec3 asVector(CameraAssembly.Point p) { return new Vec3(p.x(),p.y(),p.z()); }
    private Vec3 exchangedItemPoint() {
        return asVector(exchange.itemPoint(asPoint(HandsInvoker.cinematicExposure$position(true)),
                asPoint(HandsInvoker.cinematicExposure$position(false))));
    }
    private boolean carryingRight() { return exchange.right(); }
    private Vec3 followTarget(boolean rightHand) {
        if (aim == null || overPanel) return rest(rightHand).add(0,menu() != null && !menu().getCarried().isEmpty() ? 0.06 : 0,0);
        double tap = Math.sin(Math.PI*Motion.window(Scene.clock()-tappedAt,0,0.22));
        return handTarget(aim,Vec3.ZERO,menu() != null && !menu().getCarried().isEmpty(),tap);
    }
    @Override public void beforeFrame() {
        double now=Scene.clock();
        double dt = Math.max(0,Math.min(0.1,now-lastFrame))*ClientConfig.SPEED.get();
        if (Scene.phase != Scene.Phase.ACTIVE) {
            initialized = false;
            observePrinting();
            cancelTransfer(); printMotion = null;
            pickup.reset(); exchange.reset(true); carriedSnapshot = ItemStack.EMPTY; waitingForExchange = false;
            lastFrame = now;
            return;
        }
        observePrinting();
        exchange.advance(dt);
        if (waitingForExchange && !exchange.active()) {
            if (exchange.right() != motionRight) requestExchange(motionRight);
            else {
                waitingForExchange = false;
                motionStart = HandsInvoker.cinematicExposure$position(motionRight);
            }
        }
        if (motion != null && !waitingForExchange) {
            motion.advance(dt);
            if (motion.done()) {
                pickup.settle(menu().getCarried());
                cancelTransfer();
            }
        }
        if (printMotion != null && motion == null) {
            if (printMotion.progress() == 0) {
                printRight = HandsInvoker.cinematicExposure$position(true);
                printLeft = HandsInvoker.cinematicExposure$position(false);
            }
            printMotion.advance(dt);
            if (printMotion.done()) printMotion = null;
        }
        if (menu() != null) {
            ItemStack carried = menu().getCarried();
            boolean changed = !ItemStack.isSameItemSameComponents(carriedSnapshot,carried);
            if (changed && motion == null) {
                exchange.reset(exchange.right());
                // Retrieval starts with the hand nearest the item's usual slot; later it may cross sides.
                if (!carried.isEmpty()) for (int i=0;i<6;i++) if (menu().getSlot(i).mayPlace(carried)) {
                    exchange.reset(lateral(point(i)) >= 0); break;
                }
            }
            carriedSnapshot = carried.copy();
            boolean rightHand = carryingRight();
            var camera = Minecraft.getInstance().gameRenderer.getMainCamera();
            Vec3 facing = VisualSession.direction(camera.getYRot());
            pickup.observe(carried,HandsInvoker.cinematicExposure$position(rightHand),
                    HandReach.pocket(camera.getPosition(),facing,rightHand),followTarget(rightHand),motion == null);
            pickup.advance(dt);
            if (motion == null && !printing && !pickup.active() && !exchange.active() && aim != null) {
                boolean next = InteractionMotion.chooseRight(rightHand,lateral(aim));
                if (carried.isEmpty()) exchange.reset(next);
                else if (next != rightHand) requestExchange(next);
            }
        }
        lastFrame=now;
    }
    @Override public Vec3[] handsOverride() {
        Vec3[] targets = handTargets();
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 facing = VisualSession.direction(camera.getYRot());
        for (int i=0; i<2; i++) targets[i] = HandReach.limit(targets[i],
                HandReach.shoulder(camera.getPosition(), facing, i == 0));
        return targets;
    }
    private Vec3[] handTargets() {
        if (exchange.active()) {
            boolean targetRight = exchange.destinationRight();
            var targets = exchange.targets(asPoint(targetRight ? followTarget(true) : rest(true)),
                    asPoint(targetRight ? rest(false) : followTarget(false)));
            return new Vec3[]{asVector(targets[0]),asVector(targets[1])};
        }
        if (motion == null && !printing && printMotion == null) {
            boolean rightHand = carryingRight();
            Vec3 target = pickup.active() ? pickup.hand(followTarget(rightHand)) : followTarget(rightHand);
            return rightHand ? new Vec3[]{target,rest(false)} : new Vec3[]{rest(true),target};
        }
        Vec3 leftEdge = top.add(right().scale(-0.27)).add(side().scale(0.04)).add(0, 0.07, 0);
        Vec3 rightEdge = top.add(right().scale(0.27)).add(side().scale(0.04)).add(0, 0.07, 0);
        if (motion == null) {
            if (printMotion == null) {
                // Keep the left hand bracing the sheet and the right hand moving along its border.
                return new Vec3[]{rightEdge.add(side().scale(.012*Math.sin(Scene.clock()*2.4))),leftEdge};
            }
            double u = printMotion.progress();
            double hold = Motion.window(u,0,.3) * (printing ? 1 : 1-Motion.window(u,.72,1));
            Vec3 rightHand = printRight.lerp(rightEdge,hold)
                    .add(side().scale(.018*Math.sin(Motion.window(u,.3,.8)*Math.PI)));
            return new Vec3[]{rightHand,printLeft.lerp(leftEdge,hold)};
        }
        Vec3 target = point(transfer.slot).add(0,.035,0);
        Vec3 hand = transferHand(target);
        return motionRight ? new Vec3[]{hand,rest(false)} : new Vec3[]{rest(true),hand};
    }
    private Vec3 transferHand(Vec3 socket) {
        double t = motion.progress();
        Vec3 follow = followTarget(motionRight), above = socket.add(0,.09,0);
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 pocket = HandReach.pocket(camera.getPosition(),VisualSession.direction(camera.getYRot()),motionRight);
        if (!motion.inserting) {
            if (t < .12) return motionStart.lerp(above,Motion.window(t,0,.12));
            if (t < .26) return above.lerp(socket,Motion.window(t,.12,.24));
            if (t < .42) return socket.lerp(above,Motion.window(t,.30,.42));
            Vec3 destination = transfer.toInventory ? pocket : follow;
            if (t < .90) return above.lerp(destination,Motion.window(t,.42,.85));
            return destination.lerp(follow,Motion.window(t,.93,1));
        }
        if (motionFromHand) {
            if (t < .48) return motionStart.lerp(above,Motion.window(t,0,.48));
        } else {
            if (t < .18) return motionStart.lerp(pocket,Motion.window(t,0,.18));
            if (t < .24) return pocket;
            if (t < .48) return pocket.lerp(above,Motion.window(t,.24,.48));
        }
        if (t < .70) return above.lerp(socket,Motion.window(t,.48,.62));
        return socket.lerp(follow,Motion.window(t,.76,1));
    }
    private boolean showsMotionItem() {
        if (motion == null || waitingForExchange) return false;
        double t = motion.progress();
        return motion.inserting ? (motionFromHand || t >= .21) && !motion.contacted()
                : motion.contacted() && (!transfer.toInventory || t < .93);
    }

    /** BER is authoritative outside this single active handoff, including on leaving or correction. */
    public ItemStack displayedSlot(BlockPos block, int slot, ItemStack actual) {
        return pos.equals(block) && Scene.phase == Scene.Phase.ACTIVE && transfer != null && transfer.slot == slot
                ? transfer.resting(actual) : actual;
    }
    @Override public void renderScene(PoseStack pose, MultiBufferSource buffers, Camera camera, int light) {
        // Host 0.18.12 calls addon renderScene through both MenuRenderer and SceneRenderer.
        // Keep it empty: BER owns resting objects and renderWorld owns the one transient prop.
    }
    @Override public void renderWorld(PoseStack pose, MultiBufferSource buffers, Vec3 camera, int light) {
        if (motion != null && !waitingForExchange) {
            Vec3 hand = HandsInvoker.cinematicExposure$position(motionRight);
            motion.touch(hand.distanceToSqr(point(transfer.slot).add(0,.035,0)) < .045*.045);
        }
        // Follow the host's actual smoothed fist, not the unsmoothed animation target.
        if (waitingForExchange && motion != null) {
            Vec3 hand = exchangedItemPoint();
            ItemPlacer.renderOne(pose,buffers,camera,motion.item,hand.add(0,-0.035,0),side,pos.asLong()+transfer.slot,0.72f,0,light);
        } else if (showsMotionItem()) {
            Vec3 hand = HandsInvoker.cinematicExposure$position(motionRight);
            ItemPlacer.renderOne(pose, buffers, camera, motion.item, hand.add(0,-0.035,0), side, pos.asLong()+transfer.slot, 0.72f, 0, light);
        } else if (motion == null && !printing && pickup.showsItem() && menu() != null && !overPanel) {
            Vec3 hand = exchangedItemPoint();
            ItemPlacer.renderOne(pose, buffers, camera, menu().getCarried(), hand.add(0,-0.035,0), side, 77, 0.72f, 0, light);
        }
    }
    @Override public void onEnd() {
        cancelTransfer(); printMotion = null; clickBefore = null;
        printing = false;
        pickup.reset(); exchange.reset(true); waitingForExchange = false; carriedSnapshot = ItemStack.EMPTY;
        super.onEnd();
    }

    @Override public void renderScreen(GuiGraphics graphics, Font font, float alpha) {
        if (menu() == null) return;
        var entity = menu().getBlockEntity();
        String mode = entity.getActualPrintingMode().getSerializedName();
        for (int i=0;i<11;i++) {
            if (i < 7 && hovered != i) continue;
            double[] xy = Picking.toGui(point(i).add(0,0.04,0));
            Component label = i == 10 ? Component.translatable("cinematic_exposure.lightroom.mode."+mode)
                    : Component.translatable("cinematic_exposure.target."+LABELS[i]);
            int color = i == 10 && !menu().canChangeProcess() ? 0xFF999999 : hovered == i ? 0xFFFFCC66 : 0xFFFFFFFF;
            if (xy != null) graphics.drawCenteredString(font,label,(int)xy[0],(int)xy[1],color);
        }
        if (overPanel) {
            var mc = Minecraft.getInstance();
            int mx = (int)(mc.mouseHandler.xpos()*mc.getWindow().getGuiScaledWidth()/mc.getWindow().getScreenWidth());
            int my = (int)(mc.mouseHandler.ypos()*mc.getWindow().getGuiScaledHeight()/mc.getWindow().getScreenHeight());
            graphics.pose().pushPose();
            graphics.pose().translate(0,0,300);
            graphics.renderItem(menu().getCarried(),mx-8,my-8);
            graphics.renderItemDecorations(font,menu().getCarried(),mx-8,my-8);
            graphics.pose().popPose();
            return;
        }
        java.util.List<Component> details = new java.util.ArrayList<>();
        if (hovered == 10) {
            details.add(Component.translatable("cinematic_exposure.lightroom.mode."+mode));
            details.add(Component.translatable("cinematic_exposure.lightroom.mode."+mode+".info"));
            details.add(LightroomStatus.text(menu().canChangeProcess() ? "mode.switch" : "mode.locked"));
        } else if (hovered == 7 && status != null) {
            details.addAll(status.problems());
            if (details.isEmpty()) details.add(LightroomStatus.text("ready"));
            details.add(status.label());
            details.add(status.detail());
            if (Minecraft.getInstance().player.isCreative())
                details.add(Component.translatable("gui.exposure.lightroom.print.creative_tooltip"));
        }
        // Put explanatory text on a side rail, not at the cursor over the photo.
        int rail = Math.max(80, Math.min(150, Minecraft.getInstance().getWindow().getGuiScaledWidth()/3));
        int y = 58;
        for (Component line : details) for (var wrapped : font.split(line,rail)) {
            graphics.fill(6,y-1,10+rail,y+font.lineHeight,0xB0101010);
            graphics.drawString(font,wrapped,8,y,0xFFF4EAD4); y += font.lineHeight+2;
        }
    }

    @Override public Component hint() { return Component.translatable("cinematic_exposure.lightroom.hint"); }
}
