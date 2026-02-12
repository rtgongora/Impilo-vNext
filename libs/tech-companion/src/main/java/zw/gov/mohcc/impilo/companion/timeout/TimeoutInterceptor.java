package zw.gov.mohcc.impilo.companion.timeout;

import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.companion.context.RequestContext;

import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * Timeout decorator utility for service-level operations.
 *
 * Reads the client timeout from {@link RequestContext} and wraps
 * arbitrary work in a deadline-aware executor. Services can opt-in
 * to timeout enforcement without rewriting business logic.
 *
 * Usage:
 * <pre>
 *   var result = TimeoutInterceptor.withTimeout(() -> {
 *       return someExpensiveOperation();
 *   });
 * </pre>
 */
public final class TimeoutInterceptor {

    private TimeoutInterceptor() {
    }

    private static final ExecutorService EXECUTOR =
            Executors.newVirtualThreadPerTaskExecutor();

    /**
     * Execute the supplier within the client-specified timeout.
     * Falls back to {@code defaultTimeoutMs} if no timeout header was set.
     *
     * @throws TimeoutExceededException if the operation exceeds the timeout
     */
    public static <T> T withTimeout(Supplier<T> work, long defaultTimeoutMs) {
        RequestContext ctx = RequestContextHolder.get();
        long timeoutMs = (ctx != null && ctx.clientTimeoutMs() != null)
                ? ctx.clientTimeoutMs()
                : defaultTimeoutMs;

        Future<T> future = EXECUTOR.submit(work::get);
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new TimeoutExceededException(timeoutMs);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) throw re;
            throw new RuntimeException(cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for timeout-bounded work", e);
        }
    }

    /**
     * Execute with default 30-second timeout.
     */
    public static <T> T withTimeout(Supplier<T> work) {
        return withTimeout(work, 30_000L);
    }
}
