package io.github.donquijotee.cinematic_exposure.client;

import dev.dyslexic.cinematic.client.Picking;
import io.github.mortuusars.exposure.client.gui.screen.camera.CameraAttachmentsScreen;
import io.github.mortuusars.exposure.world.inventory.CameraInHandAttachmentsMenu;
import io.github.mortuusars.exposure.world.item.camera.Attachment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/** Presentation adapter only: vanilla container input operates Exposure's unmodified menu and slots. */
public final class CameraSceneScreen extends AbstractContainerScreen<CameraInHandAttachmentsMenu> {
    private boolean inventoryOpen;
    private Button inventoryToggle;

    public CameraSceneScreen(CameraInHandAttachmentsMenu menu) {
        super(menu, Minecraft.getInstance().player.getInventory(), Component.translatable("cinematic_exposure.camera.title"));
        imageWidth = 176;
        imageHeight = 185;
    }

    @Override protected void init() {
        super.init();
        // Keep Exposure's 18px slot grid, hotbar spacing and original slot x/y intact.
        leftPos = Math.max(4, width-imageWidth-8);
        topPos = height-imageHeight-8;
        inventoryToggle = addRenderableWidget(Button.builder(toggleLabel(), button -> toggleInventory())
                .bounds(leftPos, inventoryOpen ? topPos+69 : height-28, imageWidth, 20).build());
        isQuickCrafting = false;
        quickCraftSlots.clear();
    }

    private Component toggleLabel() {
        return Component.translatable("cinematic_exposure.camera.inventory." + (inventoryOpen ? "close" : "open"));
    }

    private void toggleInventory() {
        if (isQuickCrafting) return; // Never change visible targets in the middle of drag distribution.
        inventoryOpen = !inventoryOpen;
        hoveredSlot = null;
        inventoryToggle.setMessage(toggleLabel());
        inventoryToggle.setY(inventoryOpen ? topPos+69 : height-28);
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
            point[0] = Math.clamp(point[0], 14, Math.max(14, leftPos-14));
            point[1] = Math.clamp(point[1], 32, height-84);
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

    /** Reserve the drawer area even while closed so expanding it never moves a drag target. */
    public Vec3 workPoint(Vec3 eye, Vec3 forward, Vec3 right) {
        double available = Math.max(96, width-imageWidth-24);
        double depth = Math.max(0.75, 0.78*height/(2*Math.tan(Math.toRadians(33))*(available-16)));
        double horizontal = (available-width)/height * depth * Math.tan(Math.toRadians(33));
        return eye.add(forward.scale(depth+0.045)).add(right.scale(horizontal))
                .add(0, -0.03-depth*Math.tan(Math.toRadians(18)), 0);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
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
        graphics.drawString(font, Component.translatable("cinematic_exposure.camera.hint"), 8, 8, 0xFFFFFFFF);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override public void renderBackground(GuiGraphics graphics, int x, int y, float partialTick) {
        renderBg(graphics, partialTick, x, y); // Transparent world, no fullscreen dim/blur.
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int x, int y) {
        if (inventoryOpen) {
            // Reuse Exposure's actual inventory texture region, not a separately spaced grid.
            graphics.blit(CameraAttachmentsScreen.TEXTURE, leftPos, topPos+91, 0, 91, imageWidth, 94);
        }
    }

    @Override protected void renderLabels(GuiGraphics graphics, int x, int y) {
        if (inventoryOpen) graphics.drawString(font, playerInventoryTitle, 8, 92, 0x404040, false);
    }

    @Override protected void renderSlot(GuiGraphics graphics, Slot slot) {
        if (slot.index >= inputs() && inventoryOpen) {
            super.renderSlot(graphics, slot);
            if (!slot.mayPickup(minecraft.player)) graphics.fill(slot.x, slot.y, slot.x+16, slot.y+16, 0x60909090);
        }
    }

    @Override protected void renderSlotHighlight(GuiGraphics graphics, Slot slot, int x, int y, float partialTick) {
        if (slot.index >= inputs()) { super.renderSlotHighlight(graphics, slot, x, y, partialTick); return; }
        double[] point = attachmentPoint(slot.index);
        if (point != null) renderSlotHighlight(graphics, (int)point[0]-leftPos-8, (int)point[1]-topPos-8, 0);
    }

    @Override protected boolean isHovering(int x, int y, int w, int h, double mouseX, double mouseY) {
        for (int i=0; i<inputs(); i++) {
            Slot slot = menu.getSlot(i);
            if (x == slot.x && y == slot.y) {
                double[] point = attachmentPoint(i);
                // Attachments can never intercept clicks in the inventory drawer.
                if (point == null || inDrawer(mouseX, mouseY)) return false;
                return Math.abs(mouseX-point[0]) <= 10 && Math.abs(mouseY-point[1]) <= 10;
            }
        }
        return inventoryOpen && super.isHovering(x, y, w, h, mouseX, mouseY);
    }

    private boolean inDrawer(double x, double y) {
        return inventoryOpen && x >= leftPos && x < leftPos+imageWidth && y >= topPos+91 && y < topPos+185;
    }

    @Override protected boolean hasClickedOutside(double x, double y, int left, int top, int button) {
        // The transparent scene is an interaction area, not a vanilla "drop outside the window" target.
        return false;
    }

    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_TAB) { toggleInventory(); return true; }
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
