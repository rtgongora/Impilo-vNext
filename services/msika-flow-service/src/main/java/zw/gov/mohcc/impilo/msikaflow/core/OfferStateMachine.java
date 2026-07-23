package zw.gov.mohcc.impilo.msikaflow.core;

import zw.gov.mohcc.impilo.msikaflow.domain.OfferStatus;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import static zw.gov.mohcc.impilo.msikaflow.domain.OfferStatus.*;

/**
 * Guarded transition map for the §9.4 offer machine (OF-B6).
 *
 * <p>TTL semantics (binding, §9.4): the offer TTL is the vendor's availability
 * promise while {@code ACTIVE}; from {@code SELECTED} onward the offer TTL is
 * slaved to the DURA reservation TTL — both expire together, fail-close. The
 * re-binding of {@code ttlExpiresAt} happens in {@code CommitmentService}
 * (commitment step 5); this class enforces the legal moves.</p>
 */
public final class OfferStateMachine {

    private static final Map<OfferStatus, Set<OfferStatus>> ALLOWED = new EnumMap<>(OfferStatus.class);

    static {
        ALLOWED.put(DRAFT, Set.of(SUBMITTED, WITHDRAWN));
        ALLOWED.put(SUBMITTED, Set.of(ACTIVE, WITHDRAWN, EXPIRED));
        ALLOWED.put(ACTIVE, Set.of(SELECTED, WITHDRAWN, EXPIRED, NOT_SELECTED));
        // Withdrawal is not legal while SELECTED-in-commit (§9.4 guard) — the
        // race resolves through REVALIDATING/FAILED_REVALIDATION instead.
        ALLOWED.put(SELECTED, Set.of(REVALIDATING, FAILED_REVALIDATION, EXPIRED, NOT_SELECTED));
        ALLOWED.put(REVALIDATING, Set.of(COMMITTED, FAILED_REVALIDATION));
        ALLOWED.put(COMMITTED, Collections.emptySet());
        ALLOWED.put(NOT_SELECTED, Collections.emptySet());
        ALLOWED.put(WITHDRAWN, Collections.emptySet());
        ALLOWED.put(EXPIRED, Collections.emptySet());
        ALLOWED.put(FAILED_REVALIDATION, Collections.emptySet());
    }

    private OfferStateMachine() {}

    public static boolean canTransition(OfferStatus from, OfferStatus to) {
        return ALLOWED.getOrDefault(from, Collections.emptySet()).contains(to);
    }

    public static void assertTransition(OfferStatus from, OfferStatus to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateException("Invalid offer transition: " + from + " -> " + to);
        }
    }

    public static boolean isTerminal(OfferStatus status) {
        return ALLOWED.getOrDefault(status, Collections.emptySet()).isEmpty();
    }
}
