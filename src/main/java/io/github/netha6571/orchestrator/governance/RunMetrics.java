package io.github.netha6571.orchestrator.governance;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Collects run outcomes as they happen, not after the fact.
 * Atomic counters because the engine may update metrics from
 * parallel stages (test and docs run at the same time).
 */
public final class RunMetrics {

    private final AtomicInteger stagesRun   = new AtomicInteger();
    private final AtomicInteger stagesPassed = new AtomicInteger();
    private final AtomicInteger retries     = new AtomicInteger();
    private final AtomicInteger rollbacks   = new AtomicInteger();
    private final AtomicInteger fallbacks   = new AtomicInteger();

    // MTTR tracking: sum of recovery durations and count of recoveries.
    private final AtomicReference<Duration> recoverySum = new AtomicReference<>(Duration.ZERO);
    private final AtomicInteger recoveryCount = new AtomicInteger();

    private volatile Instant startTime;
    private volatile Instant endTime;

    public void markStart() {
        startTime = Instant.now();
    }

    public void markEnd() {
        endTime = Instant.now();
    }

    public void recordRun()      { stagesRun.incrementAndGet(); }
    public void recordPass()     { stagesPassed.incrementAndGet(); }
    public void recordRetry()    { retries.incrementAndGet(); }
    public void recordRollback() { rollbacks.incrementAndGet(); }
    public void recordFallback() { fallbacks.incrementAndGet(); }

    /**
     * Record a recovery — the time from a failure to the fix that followed.
     * Used to compute mean time to recover.
     */
    public void recordRecovery(Duration duration) {
        recoverySum.updateAndGet(d -> d.plus(duration));
        recoveryCount.incrementAndGet();
    }

    // --- Accessors ---

    public int stagesRun()    { return stagesRun.get(); }
    public int stagesPassed() { return stagesPassed.get(); }
    public int retries()      { return retries.get(); }
    public int rollbacks()    { return rollbacks.get(); }
    public int fallbacks()    { return fallbacks.get(); }

    /**
     * Success rate = passed / run. Returns 0.0 if nothing ran yet.
     */
    public double successRate() {
        int run = stagesRun.get();
        return run == 0 ? 0.0 : (double) stagesPassed.get() / run;
    }

    /**
     * Mean time to recover. Zero if no recoveries happened.
     */
    public Duration meanTimeToRecover() {
        int count = recoveryCount.get();
        if (count == 0) return Duration.ZERO;
        return recoverySum.get().dividedBy(count);
    }

    /**
     * Wall-clock time from start to end. Zero if not yet finished.
     */
    public Duration totalTime() {
        if (startTime == null || endTime == null) return Duration.ZERO;
        return Duration.between(startTime, endTime);
    }

    @Override
    public String toString() {
        return String.format(
                "Metrics { ran=%d, passed=%d, rate=%.0f%%, retries=%d, "
                + "rollbacks=%d, fallbacks=%d, MTTR=%s, total=%s }",
                stagesRun(), stagesPassed(),
                successRate() * 100,
                retries(), rollbacks(), fallbacks(),
                formatDuration(meanTimeToRecover()),
                formatDuration(totalTime()));
    }

    private static String formatDuration(Duration d) {
        long ms = d.toMillis();
        if (ms < 1000) return ms + "ms";
        return String.format("%.1fs", ms / 1000.0);
    }
}
