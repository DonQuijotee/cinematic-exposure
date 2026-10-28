package io.github.donquijotee.cinematic_exposure.client;

import dev.dyslexic.cinematic.client.Picking;
import io.github.mortuusars.exposure.world.inventory.CameraInHandAttachmentsMenu;
import io.github.mortuusars.exposure.world.item.camera.Attachment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/** Presentation adapter only: vanilla container input operates Exposure's unmodified menu and slots. */
public final class CameraSceneScreen extends AbstractContainerScreen<CameraInHandAttachmentsMenu> {
    final CinematicDrawer drawer = new CinematicDrawer();

    public CameraSceneScreen(CameraInHandAttachmentsMenu menu) {
        super(menu, Minecraft.getInstance().player.getInventory(), Component.translatable("cinematic_exposure.camera.title"));
        imageWidth = 176;
        imageHeight = 185;
    }

    @Override protected void init() {
        super.init();
        leftPos = 0;
        topPos = 0;
        drawer.init(width, height);
        isQuickCrafting = false;
        quickCraftSlots.clear();
    }

    private int inputs() { return menu.slots.size()-36; }
    public int hoveredAttachment() { return hoveredSlot != null && hoveredSlot.index < inputs() ? hoveredSlot.index : -1; }
    public Motion.Kind kind(int slot) {
        Attachment<?> attachment = menu.getCamera().map((item,stack) -> item.getAttachments()).get(slot);
        if (attachment.equals(Attachment.FILM)) return Motion.Kind.FILM;
        if (attachment.equals(Attachment.FLASH)) return Motion.Kind.FLASH;
        if (attachment.equals(Attachment.LENS)) return Motion.Kind.LENS;
        return Motion.Kind.FILTER;
    }

    /** Shared geometry for hover, drag, highlight and labels; separate crowded projected targets. */
    private double[] attachmentPoint(int slot) {
        VisualSession session = VisualSession.get();
        if (session == null || session.weight() < 0.95) return null;
        double[][] points = new double[inputs()][];
        for (int i=0; i<=slot; i++) {
            Vec3 anchor = session.socket(kind(i));
            if (kind(i) == Motion.Kind.FILTER) anchor = anchor.add(session.right.scale(0.13));
            double[] point = Picking.toGui(anchor);
            if (point == null) continue;
            point[0] = Math.clamp(point[0], 14, Math.max(14, width-14));
            double minY = Math.min(height-32, Math.max(32, 6+86*drawer.scale+14));
            point[1] = Math.clamp(point[1], minY, Math.max(minY, height-84));
            for (int j=0; j<i; j++) {
                double[] other = points[j];
                if (other != null && Math.abs(point[0]-other[0]) < 24 && Math.abs(point[1]-other[1]) < 24) {
                    point[1] = other[1]+24;
                    j = -1;
                }
            }
            points[i] = point;
        }
        return points[slot];
    }

    /** Leave the same full drawer footprint clear whether it is folded or expanded. */
    public Vec3 workPoint(Vec3 eye, Vec3 forward, Vec3 right) {
        double scale = net.minecraft.util.Mth.clamp(height*0.068f/18, 0.6f, 1.25f);
        double top = 6+86*scale+12;
        double cy = (top+height-24)/2;
        double depth = Math.max(0.85, 0.60*height/(2*Math.tan(Math.toRadians(33))*Math.max(64,height-top-24)));
        double down = (2*cy/height-1)*depth*Math.tan(Math.toRadians(33));
        double pitch = Math.toRadians(18);
        return eye.add(forward.scale(0.045+depth*Math.cos(pitch)-down*Math.sin(pitch)))
                .add(0,-0.03-depth*Math.sin(pitch)-down*Math.cos(pitch),0);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        drawer.frame(width, height, mouseX, mouseY, isQuickCrafting);
        super.render(graphics, mouseX, mouseY, partialTick);
        for (int i=0; i<inputs(); i++) {
            double[] point = attachmentPoint(i);
            if (point == null) continue;
            String key = kind(i).name().toLowerCase(java.util.Locale.ROOT);
            int x = (int)point[0], y = (int)point[1];
            graphics.renderOutline(x-10, y-10, 20, 20, hoveredAttachment()==i ? 0xFFFFCC66 : 0x90707070);
            if (hoveredAttachment()==i)
                graphics.drawCenteredString(font, Component.translatable("cinematic_exposure.target."+key), x, y+12, 0xFFFFCC66);
            // Keep a small marker on the physical attachment when targets need separation.
            var session = VisualSession.get();
            double[] anchor = session == null ? null : Picking.toGui(session.socket(kind(i)));
            if (anchor != null) graphics.fill((int)anchor[0]-1, (int)anchor[1]-1, (int)anchor[0]+2, (int)anchor[1]+2, 0xFFFFCC66);
        }
        graphics.drawString(font, Component.translatable("cinematic_exposure.camera.hint"), 8, height-12, 0xFFFFFFFF);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override public void renderBackground(GuiGraphics graphics, int x, int y, float partialTick) {
        renderBg(graphics, partialTick, x, y); // Transparent world, no fullscreen dim/blur.
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int x, int y) {
        drawer.render(graphics, x, y);
    }
    @Override protected void renderLabels(GuiGraphics graphics, int x, int y) {}

    @Override protected void renderSlot(GuiGraphics graphics, Slot slot) {
        if (slot.index < inputs() || !drawer.visible(slot)) return;
        graphics.enableScissor(drawer.x, Math.max(0,drawer.y), drawer.x+drawer.width, drawer.y+drawer.height);
        graphics.pose().pushPose();
        graphics.pose().translate(drawer.slotX(slot), drawer.slotY(slot), 0);
        graphics.pose().scale(drawer.scale, drawer.scale, 1);
        graphics.pose().translate(-slot.x, -slot.y, 0);
        super.renderSlot(graphics, slot);
        if (!slot.mayPickup(minecraft.player)) graphics.fill(slot.x, slot.y, slot.x+16, slot.y+16, 0x60909090);
        graphics.pose().popPose();
        graphics.disableScissor();
    }

    @Override protected void renderSlotHighlight(GuiGraphics graphics, Slot slot, int x, int y, float partialTick) {
        if (slot.index >= inputs()) return; // Host drawer already drew its highlight.
        double[] point = attachmentPoint(slot.index);
        if (point != null) renderSlotHighlight(graphics, (int)point[0]-leftPos-8, (int)point[1]-topPos-8, 0);
    }

    @Override protected boolean isHovering(int x, int y, int w, int h, double mouseX, double mouseY) {
        if (VisualSession.get() == null || VisualSession.get().weight() < 0.99) return false;
        for (int i=0; i<inputs(); i++) {
            Slot slot = menu.getSlot(i);
            if (x == slot.x && y == slot.y) {
                double[] point = attachmentPoint(i);
                // Attachments can never intercept clicks in the inventory drawer.
                if (point == null || inDrawer(mouseX, mouseY)) return false;
                return Math.abs(mouseX-point[0]) <= 10 && Math.abs(mouseY-point[1]) <= 10;
            }
        }
        int hovered = drawer.slotAt(mouseX,mouseY);
        int inventoryIndex = hovered >= 36 ? hovered-36 : hovered;
        for (int i=inputs(); i<menu.slots.size(); i++) {
            Slot slot = menu.getSlot(i);
            if (slot.x == x && slot.y == y) return hovered >= 0 && drawer.over(mouseX,mouseY)
                    && drawer.visible(slot) && slot.getContainerSlot() == inventoryIndex;
        }
        return false;
    }

    private boolean inDrawer(double x, double y) {
        return drawer.over(x,y);
    }

    @Override protected boolean hasClickedOutside(double x, double y, int left, int top, int button) {
        // The transparent scene is an interaction area, not a vanilla "drop outside the window" target.
        return false;
    }

    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_TAB) { if (!isQuickCrafting) drawer.toggle(); return true; }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override protected void containerTick() {
        if (minecraft.player == null || minecraft.player.containerMenu != menu) minecraft.setScreen(null);
    }

    @Override public void onClose() {
        super.onClose();
        if (menu.isOpenedFromGui() && minecraft.player != null) minecraft.setScreen(new InventoryScreen(minecraft.player));
    }
}
