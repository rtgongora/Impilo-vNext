package zw.gov.mohcc.impilo.tshepo.authz.shadow;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * Sizes shadow concurrency against the resource it actually competes for: database connections.
 *
 * <h2>The failure this exists to prevent, measured live 2026-08-07</h2>
 * <p>{@code maxConcurrent} defaulted to 4. The estate pins
 * {@code SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=3} — overriding the chart's 30 — so four
 * concurrent shadow evaluations could demand more connections than the pool holds, before
 * production authorization asked for a single one. A six-persona browser run produced
 * {@code HikariPool-1 - Connection is not available … (total=3, active=3, idle=0, waiting=14)} and
 * eight {@code CannotCreateTransactionException}s.</p>
 *
 * <p>The semaphore in {@link ShadowAuthorizationController} bounded shadow <em>evaluations</em>. It
 * said nothing about their connection footprint, so the invariant the whole design rests on —
 * <b>production authorization wins every contention, always</b> — was false as built. A ceiling
 * chosen without reference to the contended resource is not a ceiling.</p>
 *
 * <h2>The rule</h2>
 * <p>Shadow may use pool size minus {@link #RESERVED_FOR_PRODUCTION}, and never more than
 * configured. If that leaves nothing, shadow does not run at all: an estate too small to measure
 * safely is one where the measurement is refused, not one where it is taken anyway.</p>
 */
@Component
public class ShadowCapacityPolicy {

    private static final Logger log = LoggerFactory.getLogger(ShadowCapacityPolicy.class);

    /**
     * Connections shadow may never touch. Two rather than one: a single reserved connection is
     * consumed by one in-flight ext_authz evaluation, leaving the next arrival to queue behind
     * shadow — which is the starvation this is here to stop, merely rarer and harder to attribute.
     */
    static final int RESERVED_FOR_PRODUCTION = 2;

    private final int effectiveMaxConcurrent;
    private final int poolSize;

    public ShadowCapacityPolicy(ShadowProbeProperties properties,
                                ObjectProvider<DataSource> dataSourceProvider) {
        this.poolSize = poolSizeOf(dataSourceProvider.getIfAvailable());
        this.effectiveMaxConcurrent = resolve(properties.getMaxConcurrent(), poolSize);

        if (!properties.isEnabled()) {
            return;
        }
        if (effectiveMaxConcurrent < 1) {
            log.warn("shadow probe DISABLED by capacity policy: datasource pool={} leaves no "
                            + "headroom above the {} connections reserved for production "
                            + "authorization. The measurement is refused rather than taken at the "
                            + "expense of the thing it measures.",
                    poolSize, RESERVED_FOR_PRODUCTION);
        } else if (effectiveMaxConcurrent < properties.getMaxConcurrent()) {
            log.warn("shadow probe concurrency reduced {} -> {}: datasource pool={} with {} "
                            + "reserved for production authorization.",
                    properties.getMaxConcurrent(), effectiveMaxConcurrent, poolSize,
                    RESERVED_FOR_PRODUCTION);
        }
    }

    /**
     * @return permits shadow may hold, or 0 meaning "do not run". Never exceeds what was
     *     configured — this only ever lowers, so raising the pool cannot silently raise shadow load.
     */
    static int resolve(int configured, int poolSize) {
        if (poolSize <= 0) {
            // Pool size unknown. Assume the tightest realistic estate rather than the chart's
            // generous default: guessing high here is how the original defect was introduced.
            return Math.min(configured, 1);
        }
        return Math.max(0, Math.min(configured, poolSize - RESERVED_FOR_PRODUCTION));
    }

    private static int poolSizeOf(DataSource dataSource) {
        if (dataSource instanceof HikariDataSource hikari) {
            return hikari.getMaximumPoolSize();
        }
        return -1;
    }

    public int effectiveMaxConcurrent() { return effectiveMaxConcurrent; }

    /** False when the estate has no headroom to measure in. Treated exactly like the feature gate. */
    public boolean permitted() { return effectiveMaxConcurrent >= 1; }

    public int observedPoolSize() { return poolSize; }
}
