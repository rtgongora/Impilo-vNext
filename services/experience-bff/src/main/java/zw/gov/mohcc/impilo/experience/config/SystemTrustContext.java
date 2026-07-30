package zw.gov.mohcc.impilo.experience.config;

import java.util.UUID;

/**
 * Trust context for work that has no inbound request — a {@code @Scheduled} sweep or a
 * Kafka consumer.
 *
 * <p>{@code ServiceClientConfig.trustHeaderForwardingInterceptor} sources every trust
 * header from the inbound {@code HttpServletRequest}. Background work has none, so its
 * downstream calls go out with no tenant at all — and tenant is the isolation boundary
 * every sovereign service reads. {@code ContextPropagatingFanout} does not solve this: it
 * propagates a context captured from a servlet thread, and a scheduled job has nothing to
 * capture.</p>
 *
 * <h3>What this deliberately does NOT carry</h3>
 * <p>No {@code X-Actor-ID}. A sweep that re-proves whether person X still holds context C
 * is the platform asking a question <em>about</em> X, not X acting — stamping the actor's
 * identity on the call would be impersonation, and would attribute the sweep's reads to a
 * person who did not make them. The actor is passed as a query parameter by the caller
 * that needs it; the authority on the wire is this workload's own service credential,
 * which the interceptor already attaches, and {@code X-Actor-Type: SYSTEM} says so
 * plainly in every audit record.</p>
 *
 * <p>This is a background-only mechanism by construction: it is consulted solely when
 * there is no request context, so nothing on a request path can reach it.</p>
 */
public final class SystemTrustContext {

    /**
     * @param tenantId      the isolation boundary the background work is operating within
     * @param requestId     per-cycle id, so a sweep's downstream calls are traceable
     * @param correlationId groups every call made for one unit of background work
     * @param systemName    which sweep this is, for audit attribution
     */
    public record Context(String tenantId, String requestId, String correlationId, String systemName) {}

    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();

    private SystemTrustContext() {}

    /** @return the active background context, or null when running on a request thread. */
    public static Context current() {
        return CURRENT.get();
    }

    /**
     * Runs {@code work} with a background trust context bound to the current thread, always
     * clearing it afterwards so a pooled thread cannot leak one unit of work's tenant into
     * the next.
     */
    public static <T> T callWith(String tenantId, String systemName, java.util.function.Supplier<T> work) {
        Context previous = CURRENT.get();
        CURRENT.set(new Context(tenantId, UUID.randomUUID().toString(), UUID.randomUUID().toString(), systemName));
        try {
            return work.get();
        } finally {
            if (previous != null) {
                CURRENT.set(previous);
            } else {
                CURRENT.remove();
            }
        }
    }

    public static void runWith(String tenantId, String systemName, Runnable work) {
        callWith(tenantId, systemName, () -> {
            work.run();
            return null;
        });
    }
}
