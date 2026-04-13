package zw.gov.mohcc.impilo.experience.resilience;

import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.decorators.Decorators;
import io.github.resilience4j.retry.RetryRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Resilient service caller — wraps every downstream sovereign service call
 * with circuit breaker, bulkhead isolation, and retry.
 *
 * <p>If the sovereign service is down, the circuit breaker opens and
 * subsequent calls fail fast with {@link ServiceUnavailableException}
 * instead of waiting for timeout. The BFF returns HTTP 503 with a
 * structured error identifying which service is unavailable.</p>
 *
 * <p>Bulkhead isolation ensures that a slow/failing service cannot
 * consume all BFF threads — each service gets its own concurrency pool.</p>
 */
@Component
public class ResilientServiceClient {

    private static final Logger log = LoggerFactory.getLogger(ResilientServiceClient.class);

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final BulkheadRegistry bulkheadRegistry;
    private final RetryRegistry retryRegistry;

    public ResilientServiceClient(CircuitBreakerRegistry circuitBreakerRegistry,
                                   BulkheadRegistry bulkheadRegistry,
                                   RetryRegistry retryRegistry) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.bulkheadRegistry = bulkheadRegistry;
        this.retryRegistry = retryRegistry;
    }

    /**
     * Call a sovereign service with circuit breaker + bulkhead + retry protection.
     *
     * @param serviceName the Resilience4j instance name (e.g. "pct-service")
     * @param serviceCall the actual HTTP call to the sovereign service
     * @return the result from the sovereign service
     * @throws ServiceUnavailableException if the service is down or overloaded
     */
    public <T> T call(String serviceName, Supplier<T> serviceCall) {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(serviceName);
        var bh = bulkheadRegistry.bulkhead(serviceName);
        var retry = retryRegistry.retry(serviceName);

        Supplier<T> decorated = Decorators.ofSupplier(serviceCall)
                .withCircuitBreaker(cb)
                .withBulkhead(bh)
                .withRetry(retry)
                .decorate();

        try {
            return decorated.get();
        } catch (CallNotPermittedException e) {
            log.warn("Circuit OPEN for {} — failing fast", serviceName);
            throw new ServiceUnavailableException(serviceName,
                    "Service temporarily unavailable (circuit open after repeated failures)");
        } catch (BulkheadFullException e) {
            log.warn("Bulkhead FULL for {} — rejecting request", serviceName);
            throw new ServiceUnavailableException(serviceName,
                    "Service overloaded (too many concurrent requests)");
        } catch (ServiceUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.error("Call to {} failed: {}", serviceName, e.getMessage());
            throw new ServiceUnavailableException(serviceName,
                    "Service call failed: " + e.getMessage());
        }
    }

    /**
     * Call with graceful null return on failure (for optional data in aggregations).
     */
    public <T> T callOrNull(String serviceName, Supplier<T> serviceCall) {
        try {
            return call(serviceName, serviceCall);
        } catch (ServiceUnavailableException e) {
            log.debug("Graceful null for {} — {}", serviceName, e.getReason());
            return null;
        }
    }
}
