package io.github.donquijotee.cinematic_exposure.client;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Visual-only inventory retrieval: descend empty, reveal below the frame, then return holding the item. */
public final class HandPickup {
    private static final double DURATION = 0.75;
    private ItemStack observed = ItemStack.EMPTY;
    private double time = DURATION;
    private Vec3 start = Vec3.ZERO, pocket = Vec3.ZERO, rest = Vec3.ZERO;

    public void observe(ItemStack stack, Vec3 from, Vec3 pocket, Vec3 rest, boolean animate) {
        if (ItemStack.isSameItemSameComponents(observed, stack)) return;
        observed = stack.copy();
        this.start = from;
        this.pocket = pocket;
        this.rest = rest;
        time = !stack.isEmpty() && animate ? 0 : DURATION;
    }

    public void advance(double dt) { time = Math.min(DURATION, time + Math.max(0, dt)); }
    public void reset() { observed = ItemStack.EMPTY; time = DURATION; }
    public boolean active() { return time < DURATION; }
    public boolean showsItem() { return !observed.isEmpty() && time / DURATION >= 0.5; }
    public boolean holds(ItemStack stack) {
        return showsItem() && !active() && ItemStack.isSameItemSameComponents(observed, stack);
    }
    public Vec3 hand() {
        return hand(rest);
    }

    /** Keep the pickup's empty-hand descent, but let its return meet the live cursor smoothly. */
    public Vec3 hand(Vec3 destination) {
        double t = time / DURATION;
        if (t < 0.45) return start.lerp(pocket, Motion.window(t, 0, 0.45));
        if (t < 0.55) return pocket;
        return pocket.lerp(destination, Motion.window(t, 0.55, 1));
    }
}
