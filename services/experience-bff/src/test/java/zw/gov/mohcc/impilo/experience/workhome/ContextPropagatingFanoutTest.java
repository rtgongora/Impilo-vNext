package zw.gov.mohcc.impilo.experience.workhome;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ContextPropagatingFanoutTest {

    private final ContextPropagatingFanout fanout = new ContextPropagatingFanout();

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        MDC.clear();
    }

    @Test
    void run_emptyTaskList_returnsEmptyResultsWithoutSubmittingWork() {
        assertThat(fanout.run(List.of())).isEmpty();
    }

    @Test
    void run_propagatesInboundRequestAttributesOntoTheWorkerThread() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-ID", "tenant-77");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        AtomicReference<String> seenTenantHeader = new AtomicReference<>();
        FanoutTask<String> task = FanoutTask.of("facility-clinical", () -> {
            HttpServletRequest inbound =
                    ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
            seenTenantHeader.set(inbound.getHeader("X-Tenant-ID"));
            return "done";
        });

        List<FanoutResult<String>> results = fanout.run(List.of(task));

        assertThat(seenTenantHeader.get()).isEqualTo("tenant-77");
        assertThat(results).hasSize(1);
        assertThat(results.get(0).isOk()).isTrue();
        assertThat(results.get(0).value()).isEqualTo("done");
    }

    @Test
    void run_propagatesMdcOntoTheWorkerThread() {
        MDC.put("correlation_id", "corr-42");

        AtomicReference<String> seenCorrelationId = new AtomicReference<>();
        FanoutTask<Void> task = FanoutTask.of("department", () -> {
            seenCorrelationId.set(MDC.get("correlation_id"));
            return null;
        });

        fanout.run(List.of(task));

        assertThat(seenCorrelationId.get()).isEqualTo("corr-42");
    }

    @Test
    void run_returnsOneResultPerTaskInSubmittedOrder() {
        FanoutTask<String> a = FanoutTask.of("a", () -> "a-value");
        FanoutTask<String> b = FanoutTask.of("b", () -> "b-value");
        FanoutTask<String> c = FanoutTask.of("c", () -> "c-value");

        List<FanoutResult<String>> results = fanout.run(List.of(a, b, c));

        assertThat(results).extracting(FanoutResult::sectionId).containsExactly("a", "b", "c");
        assertThat(results).extracting(FanoutResult::value).containsExactly("a-value", "b-value", "c-value");
        assertThat(results).allMatch(FanoutResult::isOk);
    }

    @Test
    void run_supplierThrows_degradesToErrorInsteadOfPropagating() {
        FanoutTask<String> failing = FanoutTask.of("support", () -> {
            throw new IllegalStateException("downstream unavailable");
        });

        List<FanoutResult<String>> results = fanout.run(List.of(failing));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).status()).isEqualTo(FanoutStatus.ERROR);
        assertThat(results.get(0).errorMessage()).contains("downstream unavailable");
    }

    @Test
    void run_supplierExceedsPerTaskTimeout_degradesToTimeoutAndReturnsWithinBudget() {
        FanoutTask<String> slow = FanoutTask.of("programme", () -> {
            try {
                Thread.sleep(Duration.ofSeconds(30).toMillis());
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            return "too-late";
        });

        long start = System.nanoTime();
        List<FanoutResult<String>> results = fanout.run(List.of(slow), Duration.ofMillis(100), Duration.ofMillis(200));
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

        assertThat(results).hasSize(1);
        assertThat(results.get(0).status()).isEqualTo(FanoutStatus.TIMEOUT);
        assertThat(elapsedMillis).isLessThan(5000);
    }

    @Test
    void run_totalBudgetCapsAcrossManyFastSectionsAndOneSlowOne() {
        FanoutTask<String> fast1 = FanoutTask.of("fast-1", () -> "ok");
        FanoutTask<String> fast2 = FanoutTask.of("fast-2", () -> "ok");
        FanoutTask<String> slow = FanoutTask.of("slow", () -> {
            try {
                Thread.sleep(Duration.ofSeconds(30).toMillis());
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            return "too-late";
        });

        long start = System.nanoTime();
        List<FanoutResult<String>> results =
                fanout.run(List.of(fast1, slow, fast2), Duration.ofMillis(1200), Duration.ofMillis(300));
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

        assertThat(results).hasSize(3);
        assertThat(results.get(0).isOk()).isTrue();
        assertThat(results.get(1).status()).isEqualTo(FanoutStatus.TIMEOUT);
        assertThat(elapsedMillis).isLessThan(5000);
    }

    @Test
    void run_doesNotLeakRequestAttributesOrMdcBackToTheCallingThreadAfterATask() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        MDC.put("actor_id", "actor-1");

        fanout.run(List.of(FanoutTask.of("x", () -> "value")));

        assertThat(RequestContextHolder.getRequestAttributes()).isNotNull();
        assertThat(MDC.get("actor_id")).isEqualTo("actor-1");
    }
}
