package zw.gov.mohcc.impilo.companion.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Minimal Micrometer helper utilities for v1.1 observability baseline.
 *
 * Standard tags: tenant_id, pod_id, endpoint, class (A/B/C),
 *                decision (ALLOW/DENY/BREAK_GLASS)
 *
 * Services opt-in by injecting a {@link MeterRegistry} and calling these helpers.
 *
 * No dashboards — just metric registration and tagging conventions.
 */
public final class CompanionMetrics {

    private CompanionMetrics() {
    }

    // ── Tag Constants ───────────────────────────────────────────
    public static final String TAG_TENANT_ID = "tenant_id";
    public static final String TAG_POD_ID    = "pod_id";
    public static final String TAG_ENDPOINT  = "endpoint";
    public static final String TAG_CLASS     = "class";
    public static final String TAG_DECISION  = "decision";

    // ── Class Constants (A/B/C from Manifest v1.1) ──────────────
    /** Class A: strongly consistent, authoritative writes */
    public static final String CLASS_A = "A";
    /** Class B: eventually consistent reads, cacheable */
    public static final String CLASS_B = "B";
    /** Class C: fire-and-forget, async */
    public static final String CLASS_C = "C";

    // ── Decision Constants ──────────────────────────────────────
    public static final String DECISION_ALLOW      = "ALLOW";
    public static final String DECISION_DENY       = "DENY";
    public static final String DECISION_BREAK_GLASS = "BREAK_GLASS";

    /**
     * Create a request counter for a service endpoint.
     */
    public static Counter requestCounter(MeterRegistry registry, String serviceName,
                                         String tenantId, String podId,
                                         String endpoint, String dataClass) {
        return Counter.builder("impilo.v11.requests")
                .description("v1.1 request count by service/tenant/pod")
                .tag("service", serviceName)
                .tag(TAG_TENANT_ID, tenantId)
                .tag(TAG_POD_ID, podId)
                .tag(TAG_ENDPOINT, endpoint)
                .tag(TAG_CLASS, dataClass)
                .register(registry);
    }

    /**
     * Create a policy decision counter.
     */
    public static Counter decisionCounter(MeterRegistry registry, String serviceName,
                                          String tenantId, String podId,
                                          String decision) {
        return Counter.builder("impilo.v11.decisions")
                .description("Policy decision count")
                .tag("service", serviceName)
                .tag(TAG_TENANT_ID, tenantId)
                .tag(TAG_POD_ID, podId)
                .tag(TAG_DECISION, decision)
                .register(registry);
    }

    /**
     * Create a request duration timer for a service endpoint.
     */
    public static Timer requestTimer(MeterRegistry registry, String serviceName,
                                     String tenantId, String podId,
                                     String endpoint, String dataClass) {
        return Timer.builder("impilo.v11.request.duration")
                .description("v1.1 request duration by service/tenant/pod")
                .tag("service", serviceName)
                .tag(TAG_TENANT_ID, tenantId)
                .tag(TAG_POD_ID, podId)
                .tag(TAG_ENDPOINT, endpoint)
                .tag(TAG_CLASS, dataClass)
                .register(registry);
    }

    /**
     * Create an idempotency replay counter.
     */
    public static Counter idempotencyReplayCounter(MeterRegistry registry, String serviceName,
                                                   String tenantId, String podId) {
        return Counter.builder("impilo.v11.idempotency.replays")
                .description("Idempotency replay count")
                .tag("service", serviceName)
                .tag(TAG_TENANT_ID, tenantId)
                .tag(TAG_POD_ID, podId)
                .register(registry);
    }

    /**
     * Create a federation violation counter.
     */
    public static Counter federationViolationCounter(MeterRegistry registry,
                                                     String serviceName,
                                                     String podId) {
        return Counter.builder("impilo.v11.federation.violations")
                .description("Federation authority violation count")
                .tag("service", serviceName)
                .tag(TAG_POD_ID, podId)
                .register(registry);
    }

    /**
     * Create a timeout exceeded counter.
     */
    public static Counter timeoutCounter(MeterRegistry registry, String serviceName,
                                         String tenantId, String podId) {
        return Counter.builder("impilo.v11.timeout.exceeded")
                .description("Client timeout exceeded count")
                .tag("service", serviceName)
                .tag(TAG_TENANT_ID, tenantId)
                .tag(TAG_POD_ID, podId)
                .register(registry);
    }
}
