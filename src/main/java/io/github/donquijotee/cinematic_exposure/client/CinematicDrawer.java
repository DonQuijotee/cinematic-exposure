package io.github.donquijotee.cinematic_exposure.client;

import dev.dyslexic.cinematic.client.Anim;
import dev.dyslexic.cinematic.client.SceneScreen;
import io.github.donquijotee.cinematic_exposure.mixin.SceneScreenAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.Slot;

/** Reuses Cinematic's actual panel, slot decoration and picking, with a local presentation lifecycle. */
public final class CinematicDrawer {
    private final SceneScreen screen = new SceneScreen();
    private double expand;
    private long lastFrame;
    private boolean pinned;
    public float scale = 1;
    public int x, y, width, height;

    public static boolean owns(Object object) {
        return Minecraft.getInstance().screen instanceof CameraSceneScreen camera && camera.drawer.screen == object;
    }
    private SceneScreenAccess access() { return (SceneScreenAccess)(Object)screen; }
    public void init(int width, int height) { screen.init(Minecraft.getInstance(), width, height); lastFrame = 0; }
    public void toggle() { pinned = !pinned; }

    public void frame(int width, int height, int mouseX, int mouseY, boolean dragging) {
        long now = System.nanoTime();
        double dt = lastFrame == 0 ? 0 : Math.min(0.1, (now-lastFrame)/1e9);
        lastFrame = now;
        expand = Anim.approach(expand, pinned || dragging || over(mouseX, mouseY) ? 1 : 0, 13, dt);
        scale = Mth.clamp(height*0.068f/18, 0.6f, 1.25f);
        this.width = Math.round(172*scale);
        this.height = Math.round((28+(int)Math.round(expand*58))*scale);
        x = (width-this.width)/2;
        double presence = VisualSession.get() == null ? 0 : VisualSession.get().weight();
        y = (int)Math.round(6-(1-presence)*(this.height+10));
        var access = access();
        access.cinematicExposure$expand(expand);
        access.cinematicExposure$scale(scale);
        access.cinematicExposure$x(x);
        access.cinematicExposure$y(y);
        access.cinematicExposure$width(this.width);
        access.cinematicExposure$height(this.height);
    }

    public boolean over(double mx, double my) { return access().cinematicExposure$over(mx, my); }
    public int slotAt(double mx, double my) { return access().cinematicExposure$slotAt(mx, my); }
    public boolean visible(Slot slot) { return slot.getContainerSlot() < 9 || expand > 0.9; }
    public double slotX(Slot slot) { return x+(6+slot.getContainerSlot()%9*18)*scale; }
    public double slotY(Slot slot) {
        int index = slot.getContainerSlot();
        return y+(index < 9 ? 6 : 28+(index/9-1)*18)*scale;
    }
    public void render(GuiGraphics graphics, int mx, int my) {
        var mc = Minecraft.getInstance();
        access().cinematicExposure$draw(graphics, mc.player, over(mx,my) ? slotAt(mx,my) : -1,
                VisualSession.get() == null ? 0 : (float)VisualSession.get().weight());
    }
}
