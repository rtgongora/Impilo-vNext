package zw.gov.mohcc.impilo.experience.resilience;

import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A tiny per-source circuit-breaker cooldown for upstream-aggregation controllers
 * (e.g. {@code CommunicationController}, {@code OmnichannelController}), which fan out
 * to several optional upstreams and must briefly skip a source that just failed.
 *
 * <p>This is <strong>ephemeral resilience state</strong>, not domain data: the only
 * thing held is "source X is in cooldown until time T". Losing it on restart simply
 * means the next request retries the source immediately — there is nothing to persist,
 * and it is never a source of truth for anything a caller reads. (The doctrine that the
 * stateless BFF holds no <em>source-of-truth</em> state is therefore not violated; this
 * mirrors how in-process circuit-breakers, e.g. resilience4j, keep their own state.)</p>
 *
 * <p>Extracted from the two controllers that previously each held an identical private
 * {@code SOURCE_COOLDOWN_UNTIL} map + cooldown helpers, removing the duplication and
 * keeping resilience concerns out of the request handlers.</p>
 */
@Component
public class UpstreamSourceCircuitBreaker {

    /** Default cooldown window applied after a source failure. */
    public static final long DEFAULT_COOLDOWN_SECONDS = 30L;

    private final long cooldownSeconds;
    private final Map<String, OffsetDateTime> cooldownUntil = new ConcurrentHashMap<>();

    public UpstreamSourceCircuitBreaker() {
        this(DEFAULT_COOLDOWN_SECONDS);
    }

    UpstreamSourceCircuitBreaker(long cooldownSeconds) {
        this.cooldownSeconds = cooldownSeconds;
    }

    /** {@code true} while the given source is within its post-failure cooldown window. */
    public boolean isInCooldown(String source) {
        OffsetDateTime until = cooldownUntil.get(source);
        return until != null && until.isAfter(OffsetDateTime.now());
    }

    /** Record an upstream failure for the source, opening the cooldown window. */
    public void markFailure(String source) {
        cooldownUntil.put(source, OffsetDateTime.now().plusSeconds(cooldownSeconds));
    }

    /** Clear any cooldown for the source (e.g. after a successful call). */
    public void clearCooldown(String source) {
        cooldownUntil.remove(source);
    }
}
