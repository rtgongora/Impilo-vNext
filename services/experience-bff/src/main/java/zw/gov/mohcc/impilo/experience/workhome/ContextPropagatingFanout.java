package zw.gov.mohcc.impilo.experience.workhome;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Fans a Work Home composition out across virtual threads without silently dropping trust
 * context.
 *
 * <p>{@code ServiceClientConfig.trustHeaderForwardingInterceptor} reads the inbound
 * {@code HttpServletRequest} off {@link RequestContextHolder}, a non-inheritable
 * {@code ThreadLocal}. A naive {@code CompletableFuture.supplyAsync} fan-out therefore runs
 * every downstream call with no tenant, no actor, no {@code X-Work-Context-Token} and no
 * Authorization — each request looks anonymous to the PDP and every one of them would be
 * denied (or worse, silently answered by an ambient service-account token carrying the wrong
 * authority). This class captures the servlet request attributes, the Spring Security
 * context, and the MDC logging context on the calling thread and restores all three on each
 * virtual-thread task before it runs.</p>
 *
 * <p>Every submitted {@link FanoutTask} always produces a {@link FanoutResult} — a slow or
 * throwing downstream degrades its own section, it never fails the whole composition, and
 * {@link #run} never returns until every task has completed, timed out, or been interrupted
 * — nothing keeps running past the handler's return.</p>
 */
@Component
public class ContextPropagatingFanout {

    private static final Logger log = LoggerFactory.getLogger(ContextPropagatingFanout.class);

    /** Per-section budget, inside the downstream RestTemplates' 5s socket read timeout. */
    public static final Duration DEFAULT_PER_TASK_TIMEOUT = Duration.ofMillis(1200);

    /** Total composition budget across every section, regardless of task count. */
    public static final Duration DEFAULT_TOTAL_BUDGET = Duration.ofMillis(2000);

    public <T> List<FanoutResult<T>> run(List<FanoutTask<T>> tasks) {
        return run(tasks, DEFAULT_PER_TASK_TIMEOUT, DEFAULT_TOTAL_BUDGET);
    }

    public <T> List<FanoutResult<T>> run(List<FanoutTask<T>> tasks, Duration perTaskTimeout, Duration totalBudget) {
        if (tasks.isEmpty()) return List.of();

        RequestAttributes callerAttrs = RequestContextHolder.getRequestAttributes();
        SecurityContext callerSecurityContext = SecurityContextHolder.getContext();
        Map<String, String> callerMdc = MDC.getCopyOfContextMap();

        List<FanoutResult<T>> results = new ArrayList<>(tasks.size());
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            List<Future<T>> futures = new ArrayList<>(tasks.size());
            List<Long> startedAtNanos = new ArrayList<>(tasks.size());
            for (FanoutTask<T> task : tasks) {
                startedAtNanos.add(System.nanoTime());
                futures.add(executor.submit(propagating(callerAttrs, callerSecurityContext, callerMdc, task.supplier())));
            }

            long deadlineNanos = System.nanoTime() + totalBudget.toNanos();
            for (int i = 0; i < tasks.size(); i++) {
                FanoutTask<T> task = tasks.get(i);
                Future<T> future = futures.get(i);
                long startedAt = startedAtNanos.get(i);
                long remainingNanos = Math.min(perTaskTimeout.toNanos(), deadlineNanos - System.nanoTime());
                if (remainingNanos <= 0) {
                    future.cancel(true);
                    results.add(FanoutResult.timeout(task.sectionId(), millisSince(startedAt)));
                    continue;
                }
                try {
                    T value = future.get(remainingNanos, TimeUnit.NANOSECONDS);
                    results.add(FanoutResult.ok(task.sectionId(), value, millisSince(startedAt)));
                } catch (TimeoutException e) {
                    future.cancel(true);
                    long elapsed = millisSince(startedAt);
                    log.debug("work-home section '{}' timed out after {}ms", task.sectionId(), elapsed);
                    results.add(FanoutResult.timeout(task.sectionId(), elapsed));
                } catch (Exception e) {
                    long elapsed = millisSince(startedAt);
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    log.debug("work-home section '{}' failed: {}", task.sectionId(), cause.getMessage());
                    results.add(FanoutResult.error(task.sectionId(), cause.getMessage(), elapsed));
                }
            }
        } finally {
            // Not try-with-resources: ExecutorService#close() awaits termination, which could
            // block the handler indefinitely on a downstream call that ignores interruption.
            // Every future above has already been resolved or cancelled by this point.
            executor.shutdownNow();
        }
        return results;
    }

    private static long millisSince(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000;
    }

    private static <T> Callable<T> propagating(RequestAttributes attrs, SecurityContext securityContext,
                                                Map<String, String> mdcContext, Supplier<T> supplier) {
        return () -> {
            if (attrs != null) {
                RequestContextHolder.setRequestAttributes(attrs);
            }
            SecurityContextHolder.setContext(securityContext);
            if (mdcContext != null) {
                MDC.setContextMap(mdcContext);
            }
            try {
                return supplier.get();
            } finally {
                MDC.clear();
                SecurityContextHolder.clearContext();
                RequestContextHolder.resetRequestAttributes();
            }
        };
    }
}
