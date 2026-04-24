package zw.gov.mohcc.impilo.shared.visibility;

import zw.gov.mohcc.impilo.tshepo.contracts.dto.VisibilityProfile;

/**
 * Guards row-level reads when the PDP marked the request as aggregate-only.
 */
public final class AggregateVisibilityGuard {

    private AggregateVisibilityGuard() {
    }

    public static boolean blocksRowLevelDetail(VisibilityProfile profile) {
        if (profile == null) {
            return false;
        }
        if (Boolean.TRUE.equals(profile.aggregateOnly())) {
            return true;
        }
        return profile.visibilityTier() != null
                && "AGGREGATE_ONLY".equalsIgnoreCase(profile.visibilityTier());
    }
}
