package io.github.donquijotee.cinematic_exposure.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.dyslexic.cinematic.api.client.AddonMenuStation;
import dev.dyslexic.cinematic.client.*;
import io.github.donquijotee.cinematic_exposure.ClientConfig;
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
        for(int i=0;i<12;i++) {
            double distance=(i == 11 ? lightPoint() : point(i)).distanceToSqr(hit);
            if(distance<best) { best=distance; hovered=i; }
        }
        Vec3 relative = hit.subtract(top);
        if (hovered < 0 && Math.abs(relative.dot(right())) <= 0.22 && Math.abs(relative.dot(side())) <= 0.22
                && menu().getFrameByIndex(menu().getSelectedFrame()) != null) hovered = 7;
        if (hovered >= 0 && hovered < 11) aim = hovered == 7 ? top.add(right().scale(0.27)).add(side().scale(0.04)) : point(hovered);
    }
    @Override public Vec3 handTarget(Vec3 aim, Vec3 shoulder, boolean carrying, double tap) {
        return aim.add(0, (carrying?0.12:0.07)-0.035*tap, 0);
    }
    @Override public Vec3 heldItemPoint(Vec3 hand, Vec3 shoulder) { return hand.add(0,-0.055,0); }
    @Override protected void act(int button, boolean shift) {
        if(menu()==null || hovered<0 || hovered==11 || button>1) return;
        if (hovered == 10 && !menu().canChangeProcess()) return;
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
        ItemStack nextFilm=slot(0), nextResult=slot(6);
        boolean active=menu().isPrinting();
        if(initialized) {
            if(!ItemStack.matches(film,nextFilm)) motion=new Motion(Motion.Kind.FILM,!nextFilm.isEmpty(),nextFilm.isEmpty()?film:nextFilm);
            if(count(nextResult)<count(result)) motion=new Motion(Motion.Kind.PHOTO,false,result);
            if(!printing && (active || count(nextResult)>count(result))) motion=new Motion(Motion.Kind.PRINT,true,ItemStack.EMPTY);
            if (printing && !active && motion != null && motion.kind == Motion.Kind.PRINT) motion = null;
        }
        initialized=true; film=nextFilm.copy(); result=nextResult.copy(); printing=active;
        if(!ClientConfig.LIGHTROOM.get()) Scene.leave();
    }
    @Override public void beforeFrame() {
        double now=Scene.clock();
        if(motion!=null) { motion.advance(Math.max(0,Math.min(0.1,now-lastFrame))*ClientConfig.SPEED.get()); if(motion.done()) motion=null; }
        lastFrame=now;
    }
    @Override public Vec3[] handsOverride() {
        if (motion == null && !printing) return null;
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
        return new Vec3[]{motion.hand(rest(true),rest(true).add(0,0.06,0),target),rest(false)};
    }

    /** Only hide a newly inserted film while that exact stack is travelling to its real slot. */
    public boolean movingFilm(BlockPos block, ItemStack stack) {
        return pos.equals(block) && motion != null && motion.kind == Motion.Kind.FILM && motion.inserting
                && motion.showsItem() && ItemStack.isSameItemSameComponents(stack, motion.item);
    }
    @Override public void renderScene(PoseStack pose, MultiBufferSource buffers, Camera camera, int light) {
        // Host 0.18.12 calls addon renderScene through both MenuRenderer and SceneRenderer.
        // Keep it empty: BER owns resting objects and renderWorld owns the one transient prop.
    }
    @Override public void renderWorld(PoseStack pose, MultiBufferSource buffers, Vec3 camera, int light) {
        if (status != null) {
            pose.pushPose();
            Vec3 p = lightPoint().subtract(camera);
            pose.translate(p.x, p.y+0.002, p.z);
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-side.toYRot()));
            pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90));
            var vertices = buffers.getBuffer(net.minecraft.client.renderer.RenderType.debugQuads());
            var matrix = pose.last().pose();
            vertices.addVertex(matrix,-0.045f,0.009f,0).setColor(status.color());
            vertices.addVertex(matrix,0.045f,0.009f,0).setColor(status.color());
            vertices.addVertex(matrix,0.045f,-0.009f,0).setColor(status.color());
            vertices.addVertex(matrix,-0.045f,-0.009f,0).setColor(status.color());
            pose.popPose();
        }
        if (motion != null && motion.showsItem())
            ItemPlacer.renderOne(pose, buffers, camera, motion.item, handsOverride()[0], side, 77, 0.9f, 0, light);
    }
    @Override public void onEnd() {
        motion = null;
        printing = false;
        super.onEnd();
    }
    private Vec3 lightPoint() { return top.add(side().scale(0.47)); }

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
        double[] lamp = Picking.toGui(lightPoint());
        if (status != null && lamp != null)
            graphics.drawCenteredString(font,status.label(),(int)lamp[0],(int)lamp[1],status.color());
        if (overPanel) return;
        java.util.List<Component> details = new java.util.ArrayList<>();
        if (hovered == 11 && status != null) {
            details.add(status.label()); details.add(status.detail());
            details.add(LightroomStatus.text("light.note"));
        } else if (hovered == 10) {
            details.add(Component.translatable("cinematic_exposure.lightroom.mode."+mode));
            details.add(Component.translatable("cinematic_exposure.lightroom.mode."+mode+".info"));
            details.add(LightroomStatus.text(menu().canChangeProcess() ? "mode.switch" : "mode.locked"));
        } else if (hovered == 7 && status != null) {
            details.addAll(status.problems());
            if (details.isEmpty()) details.add(LightroomStatus.text("ready"));
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
