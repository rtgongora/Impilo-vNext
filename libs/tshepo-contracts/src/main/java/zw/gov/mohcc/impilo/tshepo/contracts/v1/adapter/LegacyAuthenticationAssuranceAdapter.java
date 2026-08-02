package zw.gov.mohcc.impilo.tshepo.contracts.v1.adapter;

import zw.gov.mohcc.impilo.tshepo.contracts.v1.AuthenticationAssurance;
import zw.gov.mohcc.impilo.tshepo.contracts.v1.RecoveryAuthenticationState;

/**
 * Compatibility between legacy {@code dto.AuthenticationAssurance} and canonical v1.
 *
 * <p>Must not silently broaden authority: constrained recovery never becomes ordinary
 * workforce AAL2 on the legacy wire.</p>
 */
@CompatibilityAdapterMeta(
        owner = "tshepo-authz-service / trust-plane",
        provenCallers = {
                "services/tshepo-authz-service/.../session/KeycloakAdapter.java",
                "services/tshepo-authz-service/.../core/PolicyEngine.java",
                "services/tshepo-authz-service/.../dto/AuthzInternalRequest.java",
                "services/tshepo-authz-service/.../api/AuthorizeController.java"
        },
        legacyShape = "zw.gov.mohcc.impilo.tshepo.contracts.dto.AuthenticationAssurance",
        canonicalShape = "zw.gov.mohcc.impilo.tshepo.contracts.v1.AuthenticationAssurance",
        removalCondition = "All proven callers consume v1 AuthenticationAssurance with RecoveryAuthenticationState; legacy dto retired after cohort migration."
)
public final class LegacyAuthenticationAssuranceAdapter {

    private LegacyAuthenticationAssuranceAdapter() {}

    public static AuthenticationAssurance toCanonical(
            zw.gov.mohcc.impilo.tshepo.contracts.dto.AuthenticationAssurance legacy) {
        if (legacy == null) {
            return AuthenticationAssurance.none();
        }
        RecoveryAuthenticationState recovery =
                AuthenticationAssurance.recoveryStateFromAmr(legacy.methods());
        return AuthenticationAssurance.of(
                legacy.aal(),
                legacy.methods(),
                legacy.authenticationTime(),
                legacy.stepUpTime(),
                legacy.phishingResistant(),
                legacy.sessionId(),
                legacy.flowId(),
                recovery);
    }

    /**
     * Legacy wire cannot express constrained recovery. Authority is preserved by demoting
     * numeric AAL to at most 1 while retaining recovery AMR markers.
     */
    public static zw.gov.mohcc.impilo.tshepo.contracts.dto.AuthenticationAssurance toLegacy(
            AuthenticationAssurance canonical) {
        if (canonical == null) {
            return zw.gov.mohcc.impilo.tshepo.contracts.dto.AuthenticationAssurance.none();
        }
        int aal = canonical.aal();
        if (canonical.isConstrainedRecovery()) {
            aal = Math.min(aal, 1);
        }
        return new zw.gov.mohcc.impilo.tshepo.contracts.dto.AuthenticationAssurance(
                aal,
                canonical.amr(),
                canonical.authenticationTime(),
                canonical.stepUpTime(),
                canonical.phishingResistant(),
                canonical.sessionId(),
                canonical.flowId());
    }
}
