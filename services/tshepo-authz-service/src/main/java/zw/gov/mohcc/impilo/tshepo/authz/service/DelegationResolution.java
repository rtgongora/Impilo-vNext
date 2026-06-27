package zw.gov.mohcc.impilo.tshepo.authz.service;

import java.util.List;

/**
 * The trust plane's view of a delegation relationship (resolved from Mvumo) for an
 * "actor acting for subject" request. Mvumo confirms an ACTIVE, unexpired relationship and
 * returns its facts; the PolicyEngine (Step 4.5) makes the access decision (scope + assurance).
 */
public record DelegationResolution(boolean active, List<String> scope, int assuranceFloor, String relationshipType) {

    public static DelegationResolution inactive() {
        return new DelegationResolution(false, List.of(), 0, null);
    }
}
