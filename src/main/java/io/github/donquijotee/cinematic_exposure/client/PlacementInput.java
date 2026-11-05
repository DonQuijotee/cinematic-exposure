package io.github.donquijotee.cinematic_exposure.client;

import java.util.HashSet;
import java.util.Set;
import java.util.function.BooleanSupplier;

/** Local input admission only. A rejected press never becomes a delayed placement on release. */
public final class PlacementInput {
    private final Set<Integer> ignoredButtons = new HashSet<>();
    private int depth;

    public boolean rejectPress(int button, boolean busy) {
        if (busy) { ignoredButtons.add(button); return true; }
        ignoredButtons.remove(button);
        return false;
    }
    public boolean rejectDrag(int button, boolean busy) { return busy || ignoredButtons.contains(button); }
    public boolean rejectRelease(int button, boolean busy) { return ignoredButtons.remove(button) || busy; }
    public boolean finishing() { return depth > 0; }
    public boolean finish(BooleanSupplier input) {
        depth++;
        try { return input.getAsBoolean(); }
        finally { depth--; }
    }
}
