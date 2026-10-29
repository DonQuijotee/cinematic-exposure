package io.github.donquijotee.cinematic_exposure.client;

import dev.dyslexic.cinematic.client.CameraRig;
import dev.dyslexic.cinematic.client.Scene;
import io.github.donquijotee.cinematic_exposure.ClientConfig;
import io.github.mortuusars.exposure.client.gui.screen.LightroomScreen;
import io.github.mortuusars.exposure.client.gui.screen.camera.CameraAttachmentsScreen;
import io.github.mortuusars.exposure.world.block.LightroomBlock;
import io.github.mortuusars.exposure.world.block.entity.Lightroom;
import io.github.mortuusars.exposure.world.inventory.CameraInHandAttachmentsMenu;
import io.github.mortuusars.exposure.world.inventory.LightroomMenu;
import io.github.mortuusars.exposure.world.item.camera.Attachment;
import io.github.mortuusars.exposure.world.item.camera.CameraItem;
import io.github.mortuusars.exposure.world.item.StackedPhotographsItem;
import io.github.mortuusars.exposure.world.item.component.StoredItemStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Client-only presentation. The real Exposure menu remains open, and is the only authority.
 * Local prediction is observed just as the original GUI observes it; a correction replaces a stale move.
 * No authoritative inventory, attachment, player-position, recipe or networking writes occur here.
 */
public final class VisualSession {
    private static VisualSession current;
    private static long lastNanos;
    private static Object world;
    private final AbstractContainerMenu menu;
    public final boolean camera;
    private final List<ItemStack> slots = new ArrayList<>();
    private final ArrayDeque<Motion> pending = new ArrayDeque<>();
    public Motion motion;
    private Motion actionMotion;
    private CameraAction action;
    private boolean printing;
    private boolean leaving;
    private double weight;
    private double fromWeight;
    private double transitionTime;
    private final Vec3 originEye;
    private final float originYaw;
    public final Vec3 forward;
    public final Vec3 right;
    public Vec3 work;
    private final Vec3 desiredEye;
    private final float desiredYaw;
    private final float desiredPitch;
    public final BlockPos lightPos;
    private ItemStack cameraStack = ItemStack.EMPTY;
    private Vec3 smoothRight;
    private Vec3 smoothLeft;
    private double frameStep;
    final HandPickup pickup = new HandPickup();
    private double settle;
    private boolean motionFromHand;

    private VisualSession(AbstractContainerMenu menu, boolean camera) {
        Minecraft mc = Minecraft.getInstance();
        this.menu = menu;
        this.camera = camera;
        originEye = mc.player.getEyePosition();
        originYaw = mc.player.getYRot();
        if (camera) {
            forward = direction(originYaw);
            right = new Vec3(-forward.z, 0, forward.x);
            work = mc.screen instanceof CameraSceneScreen screen ? screen.workPoint(originEye, forward, right)
                    : originEye.add(forward.scale(0.75)).add(0, -0.32, 0);
            desiredEye = originEye.add(forward.scale(0.045)).add(0, -0.03, 0);
            desiredYaw = originYaw + framing();
            desiredPitch = 18;
            lightPos = BlockPos.containing(originEye);
        } else {
            LightroomMenu lightroom = (LightroomMenu) menu;
            BlockPos pos = lightroom.getBlockEntity().getBlockPos();
            Direction side = lightroom.getBlockEntity().getBlockState().getValue(LightroomBlock.FACING);
            forward = Vec3.atLowerCornerOf(side.getOpposite().getNormal());
            right = new Vec3(-forward.z, 0, forward.x);
            work = Vec3.atBottomCenterOf(pos).add(0, 1.02, 0).subtract(forward.scale(0.10));
            desiredEye = work.subtract(forward.scale(0.72)).add(0, 0.56, 0);
            desiredYaw = yaw(forward) + framing();
            desiredPitch = 34;
            lightPos = pos.above();
        }
        snapshot();
    }

    public static boolean supports(Screen screen) {
        var player = Minecraft.getInstance().player;
        if (player == null) return false;
        return screen instanceof CameraSceneScreen c && ClientConfig.CAMERA.get()
                && c.getMenu() == player.containerMenu
                && c.getMenu().getCamera().getStack().getItem() instanceof CameraItem;
    }

    private static float framing() { return 0; }

    public static VisualSession get() { return current; }

    public static void frame() {
        Minecraft mc = Minecraft.getInstance();
        long now = System.nanoTime();
        double dt = lastNanos == 0 ? 0 : Math.min(0.1, (now - lastNanos) / 1.0e9);
        lastNanos = now;
        if (world != mc.level || mc.player == null || mc.level == null || !mc.player.isAlive()
                || mc.getCameraEntity() != mc.player
                || !mc.options.getCameraType().isFirstPerson() || Scene.active()) {
            current = null;
            world = mc.level;
            return;
        }
        if (mc.isPaused()) return;
        if (mc.screen != null && !(mc.screen instanceof CameraSceneScreen)) {
            // A normal Exposure/inventory window must never inherit the immersive camera or arm overlay.
            current = null;
            return;
        }
        boolean supported = supports(mc.screen);
        if (current != null && supported && current.menu != mc.player.containerMenu) current = null;
        if (current == null && supported) {
            current = new VisualSession(mc.player.containerMenu, mc.screen instanceof CameraSceneScreen);
        }
        if (current == null) return;
        VisualSession s = current;
        if (mc.screen instanceof CameraSceneScreen screen && s.camera) {
            Vec3 nextWork = screen.workPoint(s.originEye, s.forward, s.right);
            if (s.work.distanceToSqr(nextWork) > 1e-8) {
                // Window/GUI scale changes invalidate captured animation anchors, never the real operation.
                s.motion = null; s.pending.clear(); s.action = null; s.actionMotion = null;
                s.smoothRight = null;
                s.pickup.reset();
                s.snapshot();
            }
            s.work = nextWork;
        }
        boolean present = supported && mc.player.containerMenu == s.menu;
        if (!s.camera) {
            LightroomMenu lm = (LightroomMenu) s.menu;
            present &= !lm.getBlockEntity().isRemoved()
                    && mc.level.getBlockEntity(lm.getBlockEntity().getBlockPos()) == lm.getBlockEntity()
                    && s.work.distanceTo(mc.player.getEyePosition()) < 5;
        } else {
            present &= s.originEye.distanceTo(mc.player.getEyePosition()) < 2;
        }
        if (!present && !s.leaving) {
            s.leaving = true;
            s.fromWeight = s.weight;
            s.transitionTime = 0;
            s.pending.clear();
            s.motion = null;
        } else if (present && s.leaving) {
            s.leaving = false;
            s.fromWeight = s.weight;
            s.transitionTime = 0;
            s.snapshot();
        }
        double step = dt * ClientConfig.SPEED.get();
        s.frameStep = step;
        s.settle = Math.max(0, s.settle-step);
        s.transitionTime += step;
        s.weight = Mth.lerp(Motion.ease(s.transitionTime / 0.65), s.fromWeight, s.leaving ? 0 : 1);
        if (!s.leaving) {
            s.detect();
            if (s.motion == null && s.weight > 0.99) {
                s.motion = s.pending.poll();
                s.motionFromHand = s.motion != null && s.motion.inserting && s.pickup.holds(s.motion.item);
            }
            if (s.motion != null) {
                s.motion.advance(step);
                if (s.motion.done()) { s.motion = null; s.settle = 0.25; }
            }
            Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
            boolean transfer = s.motion != null || !s.pending.isEmpty();
            s.pickup.observe(s.menu.getCarried(), s.smoothRight == null ? s.rest(true) : s.smoothRight,
                    HandReach.pocket(eye, s.forward, true), s.rest(true), !transfer);
            s.pickup.advance(step);
        }
        if (s.leaving && s.weight < 0.001) current = null;
    }

    /** Called after a vanilla click as well as every frame, including server corrections. */
    public static void observe() {
        if (current != null && !current.leaving) current.detect();
    }

    public static void resynchronize(int containerId) {
        // Full-content packets initialize a window or correct prediction. Never replay their entire contents.
        if (current != null && current.menu.containerId == containerId) {
            current.pending.clear();
            current.motion = null;
            current.pickup.reset();
            if (!current.camera || current.hasCamera()) current.snapshot();
        }
    }

    private void snapshot() {
        slots.clear();
        int count = camera ? attachments().size() : Lightroom.SLOTS;
        for (int i = 0; i < count; i++) slots.add(menu.getSlot(i).getItem().copy());
        if (camera) cameraStack = ((CameraInHandAttachmentsMenu) menu).getCamera().getStack().copy();
        else {
            printing = ((LightroomMenu) menu).isPrinting();
            lastResult = menu.getSlot(Lightroom.RESULT_SLOT).getItem().copy();
        }
    }

    private List<Attachment<?>> attachments() {
        return ((CameraInHandAttachmentsMenu) menu).getCamera().map((item, stack) -> item.getAttachments());
    }

    private boolean hasCamera() {
        return ((CameraInHandAttachmentsMenu) menu).getCamera().getStack().getItem() instanceof CameraItem;
    }

    private void detect() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.containerMenu != menu || camera && !hasCamera()) return;
        for (int i = 0; i < slots.size(); i++) {
            ItemStack before = slots.get(i);
            ItemStack after = menu.getSlot(i).getItem();
            // A replacement can share an item ID while storing a different film or attachment.
            // Selection/printing progress lives in ContainerData, not these stack snapshots.
            if (camera && !ItemStack.isSameItemSameComponents(before, after)) {
                Motion.Kind kind = kind(attachments().get(i));
                transfer(kind, before, after);
            } else if (!camera && i == Lightroom.FILM_SLOT && !ItemStack.matches(before, after)) {
                transfer(Motion.Kind.FILM, before, after);
            } else if (!camera && i == Lightroom.RESULT_SLOT && !before.isEmpty()
                    && photographs(after) < photographs(before)) {
                ItemStack taken = before;
                if (before.getItem() instanceof StackedPhotographsItem photos
                        && photographs(before) - photographs(after) == 1) {
                    taken = photos.getPhotographs(before).getItemUnsafe(0).copy();
                }
                enqueue(new Motion(Motion.Kind.PHOTO, false, taken));
            }
            slots.set(i, after.copy());
        }
        if (camera) cameraStack = ((CameraInHandAttachmentsMenu) menu).getCamera().getStack().copy();
        else {
            boolean active = ((LightroomMenu) menu).isPrinting();
            if (!printing && active) enqueue(new Motion(Motion.Kind.PRINT, true, ItemStack.EMPTY));
            // Creative printing is instantaneous: a new result is the authoritative observable completion.
            ItemStack result = menu.getSlot(Lightroom.RESULT_SLOT).getItem();
            if (!active && !printing && !result.isEmpty() && !ItemStack.matches(result, lastResult)) {
                if (photographs(result) > photographs(lastResult)) {
                    enqueue(new Motion(Motion.Kind.PRINT, true, ItemStack.EMPTY));
                }
            }
            lastResult = result.copy();
            printing = active;
        }
    }
    private ItemStack lastResult = ItemStack.EMPTY;

    private static int photographs(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        return stack.getItem() instanceof StackedPhotographsItem photos
                ? photos.getPhotographs(stack).size() : stack.getCount();
    }

    private static Motion.Kind kind(Attachment<?> attachment) {
        if (attachment.equals(Attachment.FILM)) return Motion.Kind.FILM;
        if (attachment.equals(Attachment.FLASH)) return Motion.Kind.FLASH;
        if (attachment.equals(Attachment.LENS)) return Motion.Kind.LENS;
        return Motion.Kind.FILTER;
    }

    private void enqueue(Motion next) {
        if (pending.size() >= 8) pending.removeFirst();
        pending.addLast(next);
    }

    private void transfer(Motion.Kind kind, ItemStack before, ItemStack after) {
        // A newer state supersedes an unfinished transfer, including a server correction.
        if (motion != null && motion.kind == kind) motion = null;
        pending.removeIf(old -> old.kind == kind);
        if (!before.isEmpty()) enqueue(new Motion(kind, false, before));
        if (!after.isEmpty()) enqueue(new Motion(kind, true, after));
    }

    public ItemStack cameraStack() {
        ItemStack display = cameraStack.copy();
        if (motion == null) return display;
        Attachment<?> attachment = attachment(motion.kind);
        if (attachment != null) {
            // This detached display copy is never returned to any menu or network code.
            // Hide the installed part until seating; retain a removed part until the hand reaches it.
            if (motion.inserting) {
                if (motion.progress() < 0.80) display.remove(attachment.component());
            } else if (motion.progress() < 0.18) {
                display.set(attachment.component(), new StoredItemStack(motion.item.copy()));
            } else display.remove(attachment.component());
        }
        return display;
    }

    private static Attachment<?> attachment(Motion.Kind kind) {
        return switch (kind) {
            case FILM -> Attachment.FILM;
            case FLASH -> Attachment.FLASH;
            case LENS -> Attachment.LENS;
            case FILTER -> Attachment.FILTER;
            default -> null;
        };
    }

    public ItemStack film() {
        if (camera) return ItemStack.EMPTY;
        if (motion != null && motion.kind == Motion.Kind.FILM && !motion.inserting && motion.progress() < 0.18) {
            return motion.item.copy();
        }
        return menu.getSlot(Lightroom.FILM_SLOT).getItem().copy();
    }
    public ItemStack photo() { return camera ? ItemStack.EMPTY : menu.getSlot(Lightroom.RESULT_SLOT).getItem().copy(); }
    public CameraAction action() {
        if (motion == null) { action = null; actionMotion = null; return null; }
        if (actionMotion != motion) {
            Vec3 outward = switch (motion.kind) {
                case FILM -> right; // The film interaction is on the right, so the arm never reaches across the body.
                case FLASH -> right;
                default -> forward.scale(-1);
            };
            action = new CameraAction(motion, smoothRight == null ? rest(true) : smoothRight,
                    rest(true), HandReach.pocket(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition(), forward, true),
                    socket(motion.kind), outward, motionFromHand);
            actionMotion = motion;
        }
        return action;
    }

    /** Full-size camera comes from below the frustum; both grip points share exactly this translation. */
    public Vec3 entranceOffset() {
        return new Vec3(0, -1.25 * (1-weight), 0).add(forward.scale(-0.14*(1-weight)));
    }

    public Vec3 smooth(boolean rightHand, Vec3 target) {
        var view = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 shoulder = HandReach.shoulder(view.getPosition(), direction(view.getYRot()), rightHand);
        target = HandReach.limit(target, shoulder);
        Vec3 previous = rightHand ? smoothRight : smoothLeft;
        Vec3 next = previous == null ? target : previous.lerp(target, 1 - Math.exp(-frameStep * 20));
        next = HandReach.limit(next, shoulder);
        if (rightHand) smoothRight = next; else smoothLeft = next;
        return next;
    }
    public double weight() { return weight; }
    public boolean canHover() { return settle <= 0 && !pickup.active(); }
    public Vec3 local(double x, double y, double z) {
        return work.add(right.scale(x)).add(0, y, 0).add(forward.scale(z));
    }
    public Vec3 rest(boolean rightHand) {
        return camera ? local(rightHand ? 0.28 : -0.13, rightHand ? -0.08 : -0.15, rightHand ? -0.09 : 0)
                : local(rightHand ? 0.28 : -0.28, 0.035, -0.20);
    }
    public Vec3 socket(Motion.Kind kind) {
        if (camera) return switch (kind) {
            case FILM -> local(0.19, -0.065, 0.10);
            case FLASH -> local(0.225, 0, -0.045);
            case LENS, FILTER -> local(0, 0, -0.26);
            default -> work;
        };
        return switch (kind) {
            case FILM -> local(-0.16, 0.055, 0.08);
            case PRINT -> local(0.16, 0.03, 0.10);
            case PHOTO -> local(0.26, 0.04, -0.08);
            default -> work;
        };
    }

    /** Clamp movement to the live eye and raycast it, so this visual adapter cannot see through walls. */
    public static CameraRig.Pose camera(Vec3 live, float yaw, float pitch) {
        VisualSession s = current;
        Minecraft mc = Minecraft.getInstance();
        if (s == null || Scene.active() || mc.level == null || mc.player == null
                || !mc.options.getCameraType().isFirstPerson()) return null;
        Vec3 target = s.desiredEye;
        Vec3 delta = target.subtract(live);
        if (delta.length() > 0.35) target = live.add(delta.normalize().scale(0.35));
        var hit = mc.level.clip(new ClipContext(live, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        if (hit.getType() != HitResult.Type.MISS) {
            Vec3 travel = hit.getLocation().subtract(live);
            target = travel.length() > 0.06 ? live.add(travel.normalize().scale(travel.length() - 0.06)) : live;
        }
        float k = (float) s.weight;
        float targetYaw = s.desiredYaw;
        float targetPitch = s.desiredPitch;
        if (!s.camera) {
            // Tethering can leave the camera beside the nominal front pose. Keep aiming at the real
            // work area from the reachable position, rather than looking parallel to the table.
            Vec3 aim = s.work.subtract(target);
            targetYaw = yaw(aim) + framing();
            targetPitch = (float) Math.toDegrees(Math.atan2(-aim.y, Math.hypot(aim.x, aim.z)));
        }
        return new CameraRig.Pose(live.lerp(target, k),
                yaw + Mth.wrapDegrees(targetYaw - yaw) * k,
                Mth.lerp(k, pitch, targetPitch), 0);
    }

    public static Vec3 direction(float yaw) {
        double radians = Math.toRadians(yaw);
        return new Vec3(-Math.sin(radians), 0, Math.cos(radians));
    }
    private static float yaw(Vec3 direction) { return (float) Math.toDegrees(Math.atan2(-direction.x, direction.z)); }
}
