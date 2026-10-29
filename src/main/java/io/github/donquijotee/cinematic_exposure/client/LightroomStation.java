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
    private ItemStack film = ItemStack.EMPTY, result = ItemStack.EMPTY;
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
    private void observeTransfers() {
        if (menu() == null) return;
        ItemStack nextFilm=slot(0), nextResult=slot(6);
        boolean active=menu().isPrinting();
        if(initialized) {
            if(!ItemStack.matches(film,nextFilm)) beginMotion(new Motion(Motion.Kind.FILM,!nextFilm.isEmpty(),nextFilm.isEmpty()?film:nextFilm));
            if(count(nextResult)<count(result)) beginMotion(new Motion(Motion.Kind.PHOTO,false,result));
            if(!printing && (active || count(nextResult)>count(result))) beginMotion(new Motion(Motion.Kind.PRINT,true,ItemStack.EMPTY));
            if (printing && !active && motion != null && motion.kind == Motion.Kind.PRINT) motion = null;
        }
        initialized=true; film=nextFilm.copy(); result=nextResult.copy(); printing=active;
    }
    private void beginMotion(Motion next) {
        motion = next;
        motionRight = next.kind != Motion.Kind.FILM;
        motionFromHand = next.inserting && pickup.holds(next.item);
        waitingForExchange = motionFromHand && (exchange.active() || exchange.right() != motionRight);
        if (exchange.active()) exchange.reset(exchange.right());
        if (waitingForExchange) requestExchange(motionRight);
        else exchange.reset(motionRight);
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
            observeTransfers();
            motion = null;
            pickup.reset(); exchange.reset(true); carriedSnapshot = ItemStack.EMPTY; waitingForExchange = false;
            lastFrame = now;
            return;
        }
        observeTransfers(); // Observe real state before scheduling any visual pickup or handover.
        exchange.advance(dt);
        if (waitingForExchange && !exchange.active()) {
            waitingForExchange = false;
            motionStart = HandsInvoker.cinematicExposure$position(motionRight);
        }
        if (motion != null && !waitingForExchange) {
            motion.advance(dt);
            if (motion.done()) motion = null;
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
        if (motion == null && !printing) {
            boolean rightHand = carryingRight();
            Vec3 target = pickup.active() ? pickup.hand(followTarget(rightHand)) : followTarget(rightHand);
            return rightHand ? new Vec3[]{target,rest(false)} : new Vec3[]{rest(true),target};
        }
        Vec3 leftEdge = top.add(right().scale(-0.27)).add(side().scale(0.04)).add(0, 0.07, 0);
        Vec3 rightEdge = top.add(right().scale(0.27)).add(side().scale(0.04)).add(0, 0.07, 0);
        if (motion == null) {
            // Keep fingers beside the sheet during the actual server-controlled printing interval.
            return new Vec3[]{rightEdge.add(0, 0.006 * Math.sin(Scene.clock()*3), 0), leftEdge};
        }
        if (motion.kind == Motion.Kind.PRINT) {
            double u = motion.progress();
            double hold = Motion.window(u, 0, 0.3) * (printing ? 1 : 1-Motion.window(u, 0.7, 1));
            Vec3 rightHand = rest(true).lerp(rightEdge, hold)
                    .add(side().scale(0.025 * Math.sin(Motion.window(u, 0.3, 0.8)*Math.PI)));
            return new Vec3[]{rightHand, rest(false).lerp(leftEdge, hold)};
        }
        Vec3 target = point(motion.kind == Motion.Kind.FILM ? 0 : 6).add(0,0.07,0);
        boolean rightHand = motionRight;
        Vec3 hand = transferHand(rightHand, target);
        return rightHand ? new Vec3[]{hand,rest(false)} : new Vec3[]{rest(true),hand};
    }
    private Vec3 transferHand(boolean rightHand, Vec3 socket) {
        double t = motion.progress();
        Vec3 rest = followTarget(rightHand);
        if (!motion.inserting) {
            if (t < 0.20) return motionStart.lerp(socket, Motion.window(t,0,0.20));
            return socket.lerp(rest, Motion.window(t,0.30,0.95));
        }
        if (motionFromHand) {
            if (t < 0.60) return motionStart.lerp(socket, Motion.window(t,0,0.60));
        } else {
            var camera = Minecraft.getInstance().gameRenderer.getMainCamera();
            Vec3 pocket = HandReach.pocket(camera.getPosition(), VisualSession.direction(camera.getYRot()), rightHand);
            if (t < 0.18) return motionStart.lerp(pocket, Motion.window(t,0,0.18));
            if (t < 0.24) return pocket;
            if (t < 0.60) return pocket.lerp(socket, Motion.window(t,0.24,0.60));
        }
        return socket.lerp(rest, Motion.window(t,0.78,1));
    }
    private boolean showsMotionItem() {
        if (motion == null || motion.kind == Motion.Kind.PRINT || waitingForExchange) return false;
        double t = motion.progress();
        return motion.inserting ? (motionFromHand || t >= 0.21) && t < 0.78 : t >= 0.20;
    }

    /** Only hide a newly inserted film while that exact stack is travelling to its real slot. */
    public boolean movingFilm(BlockPos block, ItemStack stack) {
        return pos.equals(block) && motion != null && motion.kind == Motion.Kind.FILM && motion.inserting
                && motion.progress() < 0.78 && ItemStack.isSameItemSameComponents(stack, motion.item);
    }
    @Override public void renderScene(PoseStack pose, MultiBufferSource buffers, Camera camera, int light) {
        // Host 0.18.12 calls addon renderScene through both MenuRenderer and SceneRenderer.
        // Keep it empty: BER owns resting objects and renderWorld owns the one transient prop.
    }
    @Override public void renderWorld(PoseStack pose, MultiBufferSource buffers, Vec3 camera, int light) {
        // Follow the host's actual smoothed fist, not the unsmoothed animation target.
        if (waitingForExchange && motion != null) {
            Vec3 hand = exchangedItemPoint();
            ItemPlacer.renderOne(pose,buffers,camera,motion.item,hand.add(0,-0.035,0),side,77,0.9f,0,light);
        } else if (showsMotionItem()) {
            Vec3 hand = HandsInvoker.cinematicExposure$position(motionRight);
            ItemPlacer.renderOne(pose, buffers, camera, motion.item, hand.add(0,-0.035,0), side, 77, 0.9f, 0, light);
        } else if (motion == null && !printing && pickup.showsItem() && menu() != null && !overPanel) {
            Vec3 hand = exchangedItemPoint();
            ItemPlacer.renderOne(pose, buffers, camera, menu().getCarried(), hand.add(0,-0.035,0), side, 77, 0.9f, 0, light);
        }
    }
    @Override public void onEnd() {
        motion = null;
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
