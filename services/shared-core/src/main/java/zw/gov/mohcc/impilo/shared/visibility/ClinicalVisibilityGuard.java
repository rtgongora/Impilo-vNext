package zw.gov.mohcc.impilo.shared.visibility;

import zw.gov.mohcc.impilo.tshepo.contracts.dto.VisibilityProfile;

/**
 * Blocks clinical resource payloads when clinical access is NONE or aggregate-only.
 */
public final class ClinicalVisibilityGuard {

    private ClinicalVisibilityGuard() {
    }

    public static boolean deniesClinicalRead(VisibilityProfile profile) {
        if (profile == null) {
            return false;
        }
        if (AggregateVisibilityGuard.blocksRowLevelDetail(profile)) {
            return true;
        }
        return profile.clinicalAccess() != null
                && "NONE".equalsIgnoreCase(profile.clinicalAccess());
    }
}
