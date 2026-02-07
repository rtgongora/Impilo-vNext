package zw.gov.mohcc.impilo.tshepo.sdk;

/**
 * Thread-local holder for the current request's {@link TrustContext}.
 *
 * <p>Populated by {@link filter.TrustContextFilter} at the start of each request
 * and cleared at the end. Accessible from any layer (controller, service,
 * repository) without passing the context explicitly.</p>
 */
public final class TrustContextHolder {

    private static final ThreadLocal<TrustContext> CONTEXT = new ThreadLocal<>();

    private TrustContextHolder() {}

    public static void set(TrustContext ctx) {
        CONTEXT.set(ctx);
    }

    public static TrustContext get() {
        return CONTEXT.get();
    }

    /**
     * Require a trust context to be present.
     *
     * @throws IllegalStateException if no context has been set for this thread
     */
    public static TrustContext require() {
        TrustContext ctx = CONTEXT.get();
        if (ctx == null) {
            throw new IllegalStateException(
                    "TrustContext not set — request did not pass through TrustContextFilter");
        }
        return ctx;
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
