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
    private boolean motionRight = true, clickedWithHeldItem, clickedRight;
    private TablePlacement.Path placementPath;
    private Vec3 itemOffset;
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
                && !hidesFilmPreview(pos) && menu().getFrameByIndex(menu().getSelectedFrame()) != null) hovered = 7;
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
        Vec3 reach = horizontal(aim.subtract(shoulder));
        double hover = carrying ? .075 : .045;
        return aim.add(0,TablePlacement.HAND_Y+hover-.06*tap,0).subtract(reach.scale(TablePlacement.ITEM_REACH));
    }
    @Override public double maxReach() { return HandReach.MAX; }
    @Override public double handsStiffness() { return 20 * ClientConfig.SPEED.get(); }
    @Override public Vec3 heldItemPoint(Vec3 hand, Vec3 shoulder) {
        return hand.add(horizontal(hand.subtract(shoulder)).scale(TablePlacement.ITEM_REACH))
                .add(0,-TablePlacement.HAND_Y+.012,0);
    }
    private Vec3 horizontal(Vec3 vector) {
        Vec3 flat = new Vec3(vector.x,0,vector.z);
        return flat.lengthSqr() < 1e-8 ? forward() : flat.normalize();
    }
    @Override public boolean moving() {
        return TablePlacement.busy(motion != null,motion != null && motion.done()) || super.moving();
    }
    @Override public boolean busy() { return moving(); }

    /** Cover drawer clicks/number-key/drop paths too, without blocking Escape or incoming sync. */
    public boolean blocksClick(int containerId) {
        return Scene.phase == Scene.Phase.ACTIVE && Minecraft.getInstance().screen instanceof SceneScreen
                && menu() != null && menu().containerId == containerId && menu().getBlockEntity().getBlockPos().equals(pos)
                && busy();
    }
    public boolean hidesFilmPreview(BlockPos block) {
        return pos.equals(block) && Scene.phase == Scene.Phase.ACTIVE
                && TablePlacement.hidesFilm(transfer != null,transfer == null ? -1 : transfer.slot,
                        motion != null && motion.contacted(),motion != null && motion.done());
    }
    @Override public void quickMove(int inventorySlot, int menuSlot) {
        var mc = Minecraft.getInstance();
        if (menu() == null || mc.player == null || Scene.phase != Scene.Phase.ACTIVE || busy()) return;
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
        if(menu()==null || hovered<0 || button>1 || busy()) return;
        if (hovered == 10 && !menu().canChangeProcess()) return;
        if(hovered<7) { slotClick(hovered,button,shift); return; }
        int action=switch(hovered) {
            case 7 -> shift && Minecraft.getInstance().player.isCreative()?LightroomMenu.PRINT_CREATIVE_BUTTON_ID:LightroomMenu.PRINT_BUTTON_ID;
            case 8 -> LightroomMenu.PREVIOUS_FRAME_BUTTON_ID;
            case 9 -> LightroomMenu.NEXT_FRAME_BUTTON_ID;
            default -> LightroomMenu.TOGGLE_PROCESS_BUTTON_ID;
        };
        // Original button packet and original server checks remain authoritative.
        HandsInvoker.cinematicExposure$tap();
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
        clickBefore = null;
        if (menu() == null || menu().containerId != containerId || Scene.phase != Scene.Phase.ACTIVE || busy()) return;
        synchronize(containerId);
        clickedWithHeldItem = !menu().getCarried().isEmpty() && Hands.itemInHand();
        clickedRight = !HandsInvoker.cinematicExposure$leftWorks();
        clickBefore = java.util.stream.IntStream.range(0,7).mapToObj(i -> slot(i).copy()).toList();
    }
    public void afterClick(int containerId) {
        if (menu() == null || menu().containerId != containerId || clickBefore == null) return;
        var before = clickBefore;
        clickBefore = null;
        for (int i=0;i<6;i++) {
            ItemStack was = before.get(i), now = slot(i);
            // Ordinary takes (including photographs and Shift-to-inventory) have no extra timeline.
            // The native Hands/SceneScreen immediately display the original carried stack.
            if (ItemStack.matches(was,now) || !SlotTransfer.inserting(was,now)) continue;
            ItemStack moved = SlotTransfer.moved(was,now,true);
            Motion.Kind kind = i == 0 ? Motion.Kind.FILM : Motion.Kind.MATERIAL;
            Motion next = new Motion(kind,true,moved,TablePlacement.DURATION,TablePlacement.DROP);
            beginMotion(new SlotTransfer(i,was,now,next,false));
            break; // Exposure's original quick move selects the destination; do not invent extra transfers.
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
        motion = null; transfer = null; placementPath = null;
    }
    private void beginMotion(SlotTransfer next) {
        motionRight = clickedWithHeldItem ? clickedRight : lateral(point(next.slot)) >= 0;
        motion = next.motion;
        transfer = next;
        Vec3 rest = rest(motionRight);
        Vec3 start = HandsInvoker.cinematicExposure$position(motionRight);
        Vec3 toward = horizontal(point(next.slot).subtract(rest));
        itemOffset = toward.scale(TablePlacement.ITEM_REACH).add(0,-TablePlacement.HAND_Y+.012,0);
        Vec3 grip = point(next.slot).subtract(itemOffset);
        placementPath = new TablePlacement.Path(asPoint(start),asPoint(rest),
                TablePlacement.take(asPoint(rest),asPoint(side())),asPoint(grip),clickedWithHeldItem);
    }
    private static CameraAssembly.Point asPoint(Vec3 v) { return new CameraAssembly.Point(v.x,v.y,v.z); }
    private static Vec3 asVector(CameraAssembly.Point p) { return new Vec3(p.x(),p.y(),p.z()); }
    @Override public void beforeFrame() {
        double now=Scene.clock();
        double dt = Math.max(0,Math.min(0.1,now-lastFrame))*ClientConfig.SPEED.get();
        lastFrame = now;
        if (Scene.phase != Scene.Phase.ACTIVE || menu() == null) {
            initialized = false;
            observePrinting();
            cancelTransfer(); printMotion = null; clickBefore = null;
            return;
        }
        observePrinting();
        if (motion != null) {
            motion.advance(dt);
            if (motion.done()) cancelTransfer();
        }
        if (printMotion != null && motion == null) {
            if (printMotion.progress() == 0) {
                printRight = HandsInvoker.cinematicExposure$position(true);
                printLeft = HandsInvoker.cinematicExposure$position(false);
            }
            printMotion.advance(dt);
            if (printMotion.done()) printMotion = null;
        }
    }
    @Override public Vec3[] handsOverride() {
        // Restore the host's normal cursor following, hand choice, tap and carried-item renderer.
        if (motion == null && (!printing && printMotion == null || menu() != null && !menu().getCarried().isEmpty())) return null;
        Vec3[] targets = handTargets();
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 facing = VisualSession.direction(camera.getYRot());
        for (int i=0; i<2; i++) targets[i] = HandReach.limit(targets[i],
                HandReach.shoulder(camera.getPosition(), facing, i == 0));
        return targets;
    }
    private Vec3[] handTargets() {
        if (motion != null) {
            Vec3 hand = asVector(placementPath.hand(motion.progress()));
            return motionRight ? new Vec3[]{hand,rest(false)} : new Vec3[]{rest(true),hand};
        }
        Vec3 leftEdge = top.add(right().scale(-.27)).add(side().scale(.04)).add(0,.07,0);
        Vec3 rightEdge = top.add(right().scale(.27)).add(side().scale(.04)).add(0,.07,0);
        if (printMotion == null) return new Vec3[]{rightEdge.add(side().scale(.012*Math.sin(Scene.clock()*2.4))),leftEdge};
        double u = printMotion.progress();
        double hold = Motion.window(u,0,.3) * (printing ? 1 : 1-Motion.window(u,.72,1));
        return new Vec3[]{printRight.lerp(rightEdge,hold).add(side().scale(.018*Math.sin(Motion.window(u,.3,.8)*Math.PI))),
                printLeft.lerp(leftEdge,hold)};
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
        if (motion == null) return; // Idle carried items belong exclusively to Cinematic Hands.
        Vec3 hand = HandsInvoker.cinematicExposure$position(motionRight);
        motion.touch(hand.distanceToSqr(asVector(placementPath.grip())) < .035*.035);
        if (!motion.contacted()) {
            float scale = (float)placementPath.itemScale(motion.progress());
            float tilt = (float)(heldItemTilt()*(1-Motion.window(motion.progress(),.28,TablePlacement.DROP)));
            ItemPlacer.renderOne(pose,buffers,camera,motion.item,hand.add(itemOffset),side,
                    pos.asLong()+transfer.slot,scale,tilt,light);
        }
    }
    @Override public void onEnd() {
        cancelTransfer(); printMotion = null; clickBefore = null;
        printing = false;
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
            int color = busy() || i == 10 && !menu().canChangeProcess() ? 0xFF999999 : hovered == i ? 0xFFFFCC66 : 0xFFFFFFFF;
            if (xy != null) graphics.drawCenteredString(font,label,(int)xy[0],(int)xy[1],color);
        }
        if (overPanel) return; // The original SceneScreen renders its own carried cursor icon.
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
