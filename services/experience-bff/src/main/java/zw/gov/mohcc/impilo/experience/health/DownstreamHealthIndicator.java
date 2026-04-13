package zw.gov.mohcc.impilo.experience.health;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Health indicator that reports circuit breaker states for all sovereign services.
 *
 * <p>If more than 50% of circuit breakers are OPEN, the BFF reports DOWN.
 * This allows K8s liveness probes to detect cascading failures.</p>
 */
@Component
public class DownstreamHealthIndicator implements HealthIndicator {

    private final CircuitBreakerRegistry registry;

    public DownstreamHealthIndicator(CircuitBreakerRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Health health() {
        Health.Builder builder = Health.up();
        int total = 0;
        int open = 0;

        for (CircuitBreaker cb : registry.getAllCircuitBreakers()) {
            total++;
            CircuitBreaker.State state = cb.getState();
            builder.withDetail(cb.getName(), state.name());

            if (state == CircuitBreaker.State.OPEN || state == CircuitBreaker.State.FORCED_OPEN) {
                open++;
            }
        }

        builder.withDetail("total_circuits", total);
        builder.withDetail("open_circuits", open);

        // BFF is degraded if any circuits open, DOWN if >50% open
        if (total > 0 && open > total / 2) {
            builder.down();
            builder.withDetail("reason", "More than 50% of downstream services are unavailable");
        } else if (open > 0) {
            builder.status("DEGRADED");
        }

        return builder.build();
    }
}
