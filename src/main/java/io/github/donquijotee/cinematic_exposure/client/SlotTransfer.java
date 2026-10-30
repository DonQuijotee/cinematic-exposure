package io.github.donquijotee.cinematic_exposure.client;

import net.minecraft.world.item.ItemStack;

/** One real slot delta, including a whole-stack Shift transfer, with one visual handoff. */
public final class SlotTransfer {
    public final int slot;
    public final Motion motion;
    public final boolean toInventory;
    private final TransferDisplay<ItemStack> display;

    public SlotTransfer(int slot, ItemStack before, ItemStack after, Motion motion, boolean toInventory) {
        this.slot = slot;
        this.motion = motion;
        this.toInventory = toInventory;
        display = new TransferDisplay<>(before.copy(), after.copy(), ItemStack::matches);
    }
    public boolean reconcile(ItemStack actual) { return display.reconcile(actual); }
    public ItemStack resting(ItemStack actual) { return display.resting(actual, motion.contacted() || motion.done()); }

    public static boolean inserting(ItemStack before, ItemStack after) {
        return !after.isEmpty() && (!ItemStack.isSameItemSameComponents(before,after) || after.getCount() > before.getCount());
    }
    public static ItemStack moved(ItemStack before, ItemStack after, boolean inserting) {
        ItemStack moved = (inserting ? after : before).copy();
        if (ItemStack.isSameItemSameComponents(before,after)) moved.setCount(Math.abs(after.getCount()-before.getCount()));
        return moved;
    }
}
