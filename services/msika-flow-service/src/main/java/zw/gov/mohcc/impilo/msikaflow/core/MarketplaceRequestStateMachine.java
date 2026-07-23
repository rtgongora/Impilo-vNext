package zw.gov.mohcc.impilo.msikaflow.core;

import zw.gov.mohcc.impilo.msikaflow.domain.MarketplaceRequestStatus;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import static zw.gov.mohcc.impilo.msikaflow.domain.MarketplaceRequestStatus.*;

/**
 * Guarded transition map for the §9.3 marketplace request machine (OF-B4).
 *
 * <p>Pure guard class — persistence and events are owned by
 * {@code MarketplaceRequestService}; this class only answers "is this
 * transition legal" and throws on illegal moves, mirroring the
 * {@code OrderStateMachine} transition-map idiom.</p>
 */
public final class MarketplaceRequestStateMachine {

    private static final Map<MarketplaceRequestStatus, Set<MarketplaceRequestStatus>> ALLOWED =
            new EnumMap<>(MarketplaceRequestStatus.class);

    static {
        ALLOWED.put(NOT_REQUIRED, Collections.emptySet());
        ALLOWED.put(DRAFT, Set.of(PUBLISHED, CANCELLED));
        ALLOWED.put(PUBLISHED, Set.of(COLLECTING_OFFERS, CANCELLED, EXPIRED, FAILED_NO_OFFERS));
        ALLOWED.put(COLLECTING_OFFERS, Set.of(OFFERS_AVAILABLE, FAILED_NO_OFFERS, CANCELLED, EXPIRED));
        // OFFERS_AVAILABLE → FAILED_NO_OFFERS covers "every live offer expired before the
        // selection window closed" — the honest zero-offers posture, not a silent hold.
        ALLOWED.put(OFFERS_AVAILABLE, Set.of(SELECTION_PENDING, COMMITTED, FAILED_NO_OFFERS, CANCELLED, EXPIRED));
        // SELECTION_PENDING → OFFERS_AVAILABLE is the RC-2/3/6/7 recovery: revalidation
        // failed, the patient is honestly re-offered.
        ALLOWED.put(SELECTION_PENDING, Set.of(COMMITTED, OFFERS_AVAILABLE, CANCELLED, EXPIRED));
        ALLOWED.put(COMMITTED, Collections.emptySet());
        // FAILED_NO_OFFERS → PUBLISHED = re-publish, a new round on the same lineage.
        ALLOWED.put(FAILED_NO_OFFERS, Set.of(PUBLISHED, CANCELLED));
        ALLOWED.put(CANCELLED, Collections.emptySet());
        ALLOWED.put(EXPIRED, Collections.emptySet());
    }

    private MarketplaceRequestStateMachine() {}

    public static boolean canTransition(MarketplaceRequestStatus from, MarketplaceRequestStatus to) {
        return ALLOWED.getOrDefault(from, Collections.emptySet()).contains(to);
    }

    public static void assertTransition(MarketplaceRequestStatus from, MarketplaceRequestStatus to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateException(
                    "Invalid marketplace-request transition: " + from + " -> " + to);
        }
    }

    public static boolean isTerminal(MarketplaceRequestStatus status) {
        return ALLOWED.getOrDefault(status, Collections.emptySet()).isEmpty();
    }
}
