package io.github.donquijotee.cinematic_exposure.client;

import java.util.function.BiPredicate;

/** Detached presentation snapshots. Unchanged acknowledgements preserve the current handoff. */
public final class TransferDisplay<T> {
    private final T before, after;
    private final BiPredicate<T,T> same;
    private boolean valid = true;

    public TransferDisplay(T before, T after, BiPredicate<T,T> same) {
        this.before = before;
        this.after = after;
        this.same = same;
    }
    public boolean reconcile(T actual) {
        valid &= same.test(after, actual);
        return valid;
    }
    public T resting(T actual, boolean contacted) {
        return valid && same.test(after, actual) && !contacted ? before : actual;
    }
}
