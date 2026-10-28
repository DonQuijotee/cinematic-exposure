package io.github.donquijotee.cinematic_exposure.client;

import dev.dyslexic.cinematic.client.Picking;
import io.github.mortuusars.exposure.world.inventory.CameraInHandAttachmentsMenu;
import io.github.mortuusars.exposure.world.item.camera.Attachment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;

/** Transparent item-backed scene. Only the player's inventory drawer remains in screen space. */
public final class CameraSceneScreen extends Screen implements MenuAccess<CameraInHandAttachmentsMenu> {
    private final CameraInHandAttachmentsMenu menu;
    private int hovered=-1;
    public CameraSceneScreen(CameraInHandAttachmentsMenu menu) {
        super(Component.translatable("cinematic_exposure.camera.title")); this.menu=menu;
    }
    @Override public CameraInHandAttachmentsMenu getMenu() { return menu; }
    @Override public boolean isPauseScreen() { return false; }
    public Motion.Kind kind(int slot) {
        Attachment<?> attachment=menu.getCamera().map((item,stack)->item.getAttachments()).get(slot);
        if(attachment.equals(Attachment.FILM)) return Motion.Kind.FILM;
        if(attachment.equals(Attachment.FLASH)) return Motion.Kind.FLASH;
        if(attachment.equals(Attachment.LENS)) return Motion.Kind.LENS;
        return Motion.Kind.FILTER;
    }
    public int hoveredAttachment() { return hovered; }
    private int inputs() { return menu.slots.size()-36; }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial) {
        if(minecraft.player==null || minecraft.player.containerMenu!=menu) return;
        hovered=-1;
        VisualSession s=VisualSession.get();
        double best=18*18;
        if(s!=null) for(int i=0;i<inputs();i++) {
            var point=s.socket(kind(i));
            // Separate the filter target from the lens while keeping both anchored on the camera.
            if(kind(i)==Motion.Kind.FILTER) point=point.add(s.right.scale(0.13));
            double[] xy=Picking.toGui(point);
            if(xy==null) continue;
            double d=(xy[0]-mouseX)*(xy[0]-mouseX)+(xy[1]-mouseY)*(xy[1]-mouseY);
            if(d<best) { best=d; hovered=i; }
            String key=kind(i).name().toLowerCase(java.util.Locale.ROOT);
            g.drawCenteredString(font,Component.translatable("cinematic_exposure.target."+key),(int)xy[0],(int)xy[1],0xFFFFFFFF);
        }
        int left=(width-180)/2, top=height-88;
        g.fill(left-3,top-3,left+183,top+83,0xA0202020);
        for(int i=0;i<36;i++) {
            int x=left+(i%9)*20,y=top+(i/9)*20;
            g.fill(x,y,x+18,y+18,0x90404040);
            ItemStack stack=menu.getSlot(inputs()+i).getItem();
            g.renderItem(stack,x+1,y+1); g.renderItemDecorations(font,stack,x+1,y+1);
            if(mouseX>=x && mouseX<x+18 && mouseY>=y && mouseY<y+18) {
                hovered=inputs()+i;
                if(!stack.isEmpty()) g.renderTooltip(font,stack,mouseX,mouseY);
            }
        }
        if(!menu.getCarried().isEmpty()) g.renderItem(menu.getCarried(),mouseX+8,mouseY+8);
        g.drawCenteredString(font,Component.translatable("cinematic_exposure.camera.hint"),width/2,8,0xFFFFFFFF);
    }
    @Override public boolean mouseClicked(double x,double y,int button) {
        if(button>1 || hovered<0 || minecraft.player==null || minecraft.player.containerMenu!=menu) return false;
        minecraft.gameMode.handleInventoryMouseClick(menu.containerId,hovered,button,
                hasShiftDown()?ClickType.QUICK_MOVE:ClickType.PICKUP,minecraft.player);
        return true;
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers) {
        if(minecraft.options.keyInventory.matches(key,scan)) { onClose(); return true; }
        return super.keyPressed(key,scan,modifiers);
    }
    @Override public void tick() {
        if(minecraft.player==null || minecraft.player.containerMenu!=menu) minecraft.setScreen(null);
    }
    @Override public void onClose() {
        if(minecraft.player!=null && minecraft.player.containerMenu==menu) minecraft.player.closeContainer();
        super.onClose();
    }
}
