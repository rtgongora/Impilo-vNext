package zw.gov.mohcc.impilo.tshepo.contracts.v1.adapter;

import zw.gov.mohcc.impilo.tshepo.contracts.v1.ConsentDecision;

import java.util.List;

/**
 * Compatibility between legacy {@code dto.ConsentDecision} and canonical v1 ConsentDecision.
 *
 * <p>Does <strong>not</strong> resolve Mvumo / tshepo-consent ownership or the POST/GET
 * evaluate incompatibility. Lawful basis remains a separate type.</p>
 */
@CompatibilityAdapterMeta(
        owner = "tshepo-consent-service / tshepo-authz-service",
        provenCallers = {
                "services/tshepo-authz-service/.../service/ConsentClient.java",
                "services/tshepo-consent-service/.../api/ConsentEvaluationController.java",
                "services/tshepo-consent-service/.../service/ConsentEvaluationService.java",
                "services/fhir-gateway-service/.../core/ConsentEnforcementService.java",
                "services/mvumo-service/.../service/MvumoService.java",
                "services/tshepo-authz-service/.../core/PolicyEngine.java"
        },
        legacyShape = "zw.gov.mohcc.impilo.tshepo.contracts.dto.ConsentDecision",
        canonicalShape = "zw.gov.mohcc.impilo.tshepo.contracts.v1.ConsentDecision",
        removalCondition = "Evaluate wire converged and all proven callers use v1 ConsentDecision; ownership SoR designated in architecture checkpoint."
)
public final class LegacyConsentDecisionAdapter {

    private LegacyConsentDecisionAdapter() {}

    public static ConsentDecision toCanonical(
            zw.gov.mohcc.impilo.tshepo.contracts.dto.ConsentDecision legacy,
            String subjectRef,
            String purpose) {
        if (legacy == null) {
            return ConsentDecision.deny("NO_ACTIVE_CONSENT", subjectRef, purpose);
        }
        return new ConsentDecision(
                zw.gov.mohcc.impilo.tshepo.contracts.v1.TrustContractVersion.V1,
                legacy.permitted(),
                legacy.consentId(),
                legacy.provision(),
                legacy.allowedScopes() == null ? List.of() : legacy.allowedScopes(),
                legacy.deniedScopes() == null ? List.of() : legacy.deniedScopes(),
                legacy.reason(),
                subjectRef,
                purpose);
    }

    public static zw.gov.mohcc.impilo.tshepo.contracts.dto.ConsentDecision toLegacy(ConsentDecision canonical) {
        if (canonical == null) {
            return zw.gov.mohcc.impilo.tshepo.contracts.dto.ConsentDecision.noConsentFound();
        }
        return new zw.gov.mohcc.impilo.tshepo.contracts.dto.ConsentDecision(
                canonical.permitted(),
                canonical.consentId(),
                canonical.provision(),
                canonical.allowedScopes().isEmpty() ? null : canonical.allowedScopes(),
                canonical.deniedScopes().isEmpty() ? null : canonical.deniedScopes(),
                canonical.reasonCode());
    }
}
