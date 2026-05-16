package zw.gov.mohcc.impilo.companion.context;

import java.util.Optional;

/**
 * Thread-local holder for the current request's {@link RequestContext}.
 *
 * Populated by {@link zw.gov.mohcc.impilo.companion.filter.V11HeaderFilter},
 * cleared after request processing.
 */
public final class RequestContextHolder {

    private RequestContextHolder() {
    }

    private static final ThreadLocal<RequestContext> HOLDER = new ThreadLocal<>();

    /** Set the context for the current thread. */
    public static void set(RequestContext ctx) {
        HOLDER.set(ctx);
    }

    /** Get the context, or null if not set. */
    public static RequestContext get() {
        return HOLDER.get();
    }

    /** Get the context as an Optional, never null. */
    public static Optional<RequestContext> current() {
        return Optional.ofNullable(HOLDER.get());
    }

    /**
     * Get the context; throws if not set.
     *
     * @throws IllegalStateException if no context is bound
     */
    public static RequestContext require() {
        RequestContext ctx = HOLDER.get();
        if (ctx == null) {
            throw new IllegalStateException(
                    "RequestContext not set — ensure V11HeaderFilter is registered");
        }
        return ctx;
    }

    /** Clear the context (call in filter finally block). */
    public static void clear() {
        HOLDER.remove();
    }
}
