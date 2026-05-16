package zw.gov.mohcc.impilo.llm.core;

import java.time.Instant;
import java.util.Set;
import zw.gov.mohcc.impilo.llm.core.LlmModels.LlmProviderHealthStatus;

public abstract class AbstractLlmProvider implements LlmProvider {
    private volatile long calls;
    private volatile long totalLatencyMs;
    private volatile Instant lastSuccessAt;
    private volatile Instant lastFailureAt;
    private volatile String lastError;

    protected void recordSuccess(long latencyMs) {
        calls++;
        totalLatencyMs += Math.max(0, latencyMs);
        lastSuccessAt = Instant.now();
        lastError = null;
    }

    protected void recordFailure(String error) {
        lastFailureAt = Instant.now();
        lastError = error;
    }

    @Override
    public LlmProviderHealthStatus healthStatus() {
        long avg = calls > 0 ? totalLatencyMs / calls : 0;
        boolean healthy = enabled() && (lastFailureAt == null || lastSuccessAt == null || lastSuccessAt.isAfter(lastFailureAt));
        return new LlmProviderHealthStatus(name(), enabled(), healthy, avg, lastSuccessAt, lastFailureAt, lastError);
    }

    @Override
    public abstract String name();

    @Override
    public abstract boolean enabled();

    @Override
    public abstract String model();

    @Override
    public abstract int priority();

    @Override
    public abstract Set<LlmCapability> capabilities();
}
