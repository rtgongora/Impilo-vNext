package zw.gov.mohcc.impilo.tshepo.authz.trust;

import java.util.Map;
import java.util.Set;

/**
 * Lifecycle status of a trust authority / issuer / document-signer entry, with the allowed
 * forward/backward transitions. Transitions are enforced so an entry cannot, e.g., jump from
 * DRAFT straight to ACTIVE without review.
 */
public enum TrustStatus {
    DRAFT, PENDING, ACTIVE, SUSPENDED, RETIRED;

    private static final Map<TrustStatus, Set<TrustStatus>> ALLOWED = Map.of(
            DRAFT, Set.of(PENDING, RETIRED),
            PENDING, Set.of(ACTIVE, DRAFT, RETIRED),
            ACTIVE, Set.of(SUSPENDED, RETIRED),
            SUSPENDED, Set.of(ACTIVE, RETIRED),
            RETIRED, Set.of()
    );

    public boolean canTransitionTo(TrustStatus target) {
        return ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }
}
