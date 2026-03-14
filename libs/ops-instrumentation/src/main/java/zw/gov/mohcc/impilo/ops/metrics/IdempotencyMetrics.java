package zw.gov.mohcc.impilo.ops.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Idempotency replay and conflict counters.
 *
 * <p>Services call these helpers after detecting an idempotency replay or conflict.
 * The ops-instrumentation auto-configuration wires a singleton that services can inject.
 */
public class IdempotencyMetrics {

    private final Counter replayCounter;
    private final Counter conflictCounter;

    public IdempotencyMetrics(MeterRegistry registry) {
        this.replayCounter = Counter.builder("impilo.ops.idempotency.replays")
                .description("Number of idempotent request replays")
                .register(registry);
        this.conflictCounter = Counter.builder("impilo.ops.idempotency.conflicts")
                .description("Number of idempotency key conflicts (same key, different body)")
                .register(registry);
    }

    public void recordReplay() {
        replayCounter.increment();
    }

    public void recordConflict() {
        conflictCounter.increment();
    }

    public double replayCount() {
        return replayCounter.count();
    }

    public double conflictCount() {
        return conflictCounter.count();
    }
}
