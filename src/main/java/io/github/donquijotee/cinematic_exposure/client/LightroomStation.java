package io.github.donquijotee.cinematic_exposure.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.dyslexic.cinematic.api.client.AddonMenuStation;
import dev.dyslexic.cinematic.client.*;
import io.github.donquijotee.cinematic_exposure.ClientConfig;
import io.github.mortuusars.exposure.ExposureClient;
import io.github.mortuusars.exposure.client.render.image.RenderCoordinates;
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
    private static final String[] LABELS = {"film", "paper", "cyan", "magenta", "yellow", "black", "photo",
        "print", "previous", "next", "process"};
    public LightroomStation(BlockPos pos, Direction side) {
        super(pos, side, LightroomMenu.class, 0); // Lightroom retains inputs; never return them on exit.
        top = Vec3.atBottomCenterOf(pos).add(0, 1.01, 0);
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
        double x, z;
        if (i == 0) { x=-0.32; z=-0.18; }
        else if (i == 6) { x=0.32; z=-0.18; }
        else if (i < 6) { x=(i-3)*0.16; z=0.24; }
        else { x=(i-8.5)*0.20; z=-0.39; }
        return top.add(right().scale(x)).add(side().scale(z)).add(0,0.025,0);
    }
    @Override protected Vec3 restPoint(int slot) { return point(slot); }
    @Override public void updateCursor(boolean overPanel) {
        hovered=-1; aim=null;
        if (overPanel || menu()==null) return;
        Vec3 hit=Picking.cursorOnPlane(top.y+0.025);
        if(hit==null) return;
        double best=0.105*0.105;
        for(int i=0;i<11;i++) {
            double distance=point(i).distanceToSqr(hit);
            if(distance<best) { best=distance; hovered=i; }
        }
        if(hovered>=0) aim=point(hovered);
    }
    @Override public Vec3 handTarget(Vec3 aim, Vec3 shoulder, boolean carrying, double tap) {
        return aim.add(0, (carrying?0.12:0.07)-0.035*tap, 0);
    }
    @Override public Vec3 heldItemPoint(Vec3 hand, Vec3 shoulder) { return hand.add(0,-0.055,0); }
    @Override protected void act(int button, boolean shift) {
        if(menu()==null || hovered<0 || button>1) return;
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
        ItemStack nextFilm=slot(0), nextResult=slot(6);
        boolean active=menu().isPrinting();
        if(initialized) {
            if(!ItemStack.matches(film,nextFilm)) motion=new Motion(Motion.Kind.FILM,!nextFilm.isEmpty(),nextFilm.isEmpty()?film:nextFilm);
            if(count(nextResult)<count(result)) motion=new Motion(Motion.Kind.PHOTO,false,result);
            if(!printing && (active || count(nextResult)>count(result))) motion=new Motion(Motion.Kind.PRINT,true,ItemStack.EMPTY);
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
        if(motion==null) return null;
        Vec3 target=point(motion.kind==Motion.Kind.FILM?0:motion.kind==Motion.Kind.PHOTO?6:7).add(0,0.07,0);
        Vec3 hand=motion.hand(rest(true),rest(true).add(0,0.06,0),target);
        Vec3 left=rest(false);
        if(motion.kind==Motion.Kind.PRINT) {
            double u=motion.progress();
            left=left.lerp(point(1).add(0,0.07,0), Motion.window(u,0,0.3)*(1-Motion.window(u,0.7,1)));
        }
        return new Vec3[]{hand,left};
    }
    @Override public void renderScene(PoseStack pose, MultiBufferSource buffers, Camera camera, int light) {
        for(int i=0;i<7;i++) ItemPlacer.renderOne(pose,buffers,camera.getPosition(),slot(i),point(i),side,i,0.72f,0,light);
        if(motion!=null && motion.showsItem()) ItemPlacer.renderOne(pose,buffers,camera.getPosition(),motion.item,handsOverride()[0],side,77,0.9f,0,light);
    }
    @Override public void renderWorld(PoseStack pose, MultiBufferSource buffers, Vec3 camera, int light) {}
    @Override public void renderScreen(GuiGraphics graphics, Font font, float alpha) {
        for(int i=0;i<11;i++) {
            double[] xy=Picking.toGui(point(i));
            if(xy!=null) graphics.drawCenteredString(font,Component.translatable("cinematic_exposure.target."+LABELS[i]),(int)xy[0],(int)xy[1],hovered==i?0xFFFFCC66:0xFFFFFFFF);
        }
        if(menu()!=null) {
            var frame=menu().getFrameByIndex(menu().getSelectedFrame());
            if(frame!=null) {
                var mc=Minecraft.getInstance();
                int size=Math.min(100,mc.getWindow().getGuiScaledHeight()/3);
                graphics.flush();
                var buffer=mc.renderBuffers().bufferSource();
                ExposureClient.imageRenderer().render(ExposureClient.renderedExposures().getOrCreate(frame),graphics.pose(),buffer,
                    new RenderCoordinates(mc.getWindow().getGuiScaledWidth()-size-12,mc.getWindow().getGuiScaledHeight()-size-32,size,size),io.github.mortuusars.exposure.util.color.Color.WHITE);
                buffer.endBatch();
            }
        }
    }
    @Override public Component hint() { return Component.translatable("cinematic_exposure.lightroom.hint"); }
}
