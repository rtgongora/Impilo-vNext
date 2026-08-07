package zw.gov.mohcc.impilo.tshepo.authz.shadow;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sizing shadow against the resource it contends for.
 *
 * <p>These encode a defect that was measured live, not imagined: {@code maxConcurrent=4} against
 * an estate-pinned Hikari pool of 3 produced {@code Connection is not available … (total=3,
 * active=3, idle=0, waiting=14)} and eight {@code CannotCreateTransactionException}s during a
 * six-persona browser run. The semaphore bounded shadow <em>evaluations</em> and said nothing about
 * their connection footprint, so the design's central promise — production authorization wins every
 * contention — was false while appearing to be enforced.</p>
 */
class ShadowCapacityPolicyTest {

    /** The exact live configuration that starved the pool. */
    @Test
    void theEstatesOwnPinnedPoolSizeLowersFourToOne() {
        assertEquals(1, ShadowCapacityPolicy.resolve(4, 3),
                "pool=3 minus 2 reserved leaves exactly one permit for shadow");
    }

    @Test
    void aPoolWithNoHeadroomRefusesTheMeasurementEntirely() {
        assertEquals(0, ShadowCapacityPolicy.resolve(4, 2));
        assertEquals(0, ShadowCapacityPolicy.resolve(4, 1));
        assertEquals(0, ShadowCapacityPolicy.resolve(1, 2),
                "an estate too small to measure safely refuses to measure, rather than measuring "
                        + "at the expense of the thing being measured");
    }

    /**
     * This only ever lowers. A generous pool must not silently raise shadow load above what an
     * operator configured — the blast radius of this feature stays a deliberate decision.
     */
    @Test
    void aLargePoolNeverRaisesShadowAboveWhatWasConfigured() {
        assertEquals(1, ShadowCapacityPolicy.resolve(1, 30));
        assertEquals(4, ShadowCapacityPolicy.resolve(4, 30));
        assertEquals(4, ShadowCapacityPolicy.resolve(4, 1000));
    }

    /**
     * An unknown pool assumes the tightest realistic estate. Guessing high is precisely how the
     * original defect was introduced — the chart said 30, the estate ran 3.
     */
    @Test
    void anUnknownPoolAssumesTheTightestEstateRatherThanTheChartDefault() {
        assertEquals(1, ShadowCapacityPolicy.resolve(4, -1));
        assertEquals(1, ShadowCapacityPolicy.resolve(4, 0));
    }

    /**
     * The invariant, stated precisely. The first draft asserted "production always keeps 2" and
     * failed at pool=1 — correctly, because two connections cannot be reserved from a pool of one.
     * What actually holds is stronger and simpler: shadow never takes a connection production could
     * have needed, so below the reserve shadow gets nothing at all.
     */
    @Test
    void shadowNeverTakesAConnectionProductionCouldHaveNeeded() {
        for (int pool = 1; pool <= 64; pool++) {
            int shadow = ShadowCapacityPolicy.resolve(Integer.MAX_VALUE, pool);
            assertTrue(shadow == 0 || pool - shadow >= ShadowCapacityPolicy.RESERVED_FOR_PRODUCTION,
                    "pool=" + pool + " gave shadow " + shadow + ", leaving production "
                            + (pool - shadow));
            assertTrue(pool - shadow >= Math.min(pool, ShadowCapacityPolicy.RESERVED_FOR_PRODUCTION),
                    "pool=" + pool + " left production fewer than the whole pool it started with");
        }
    }
}
