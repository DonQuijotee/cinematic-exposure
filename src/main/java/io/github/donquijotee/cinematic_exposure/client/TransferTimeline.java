package io.github.donquijotee.cinematic_exposure.client;

/** Visual time only. A contact beat waits for the rendered hand, never for a network round trip. */
public final class TransferTimeline {
    private final double duration, beat;
    private double time, waiting;
    private boolean contacted, abandoned;

    public TransferTimeline(double duration, double contactProgress) {
        this.duration = duration;
        this.beat = contactProgress < 0 ? -1 : duration * contactProgress;
    }

    public void advance(double dt) {
        if (done()) return;
        dt = Math.max(0, dt);
        double next = time + dt;
        if (beat >= 0 && !contacted && next >= beat) {
            waiting += next - Math.max(time, beat);
            time = beat;
            // An unreachable target (changed camera/world/model) must not strand a visual prop.
            // Fall back to real state; this never completes or repeats a gameplay operation.
            if (waiting > 0.45) abandoned = true;
        } else time = Math.min(duration, next);
    }

    public void touch(boolean handAtSocket) {
        if (!abandoned && beat >= 0 && time >= beat && handAtSocket) contacted = true;
    }
    public boolean contacted() { return contacted; }
    public boolean done() { return abandoned || time >= duration; }
    public double progress() { return Math.clamp(time / duration, 0, 1); }
}
