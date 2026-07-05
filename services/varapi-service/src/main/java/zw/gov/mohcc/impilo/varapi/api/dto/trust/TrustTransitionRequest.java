package zw.gov.mohcc.impilo.varapi.api.dto.trust;

/**
 * Body of {@code POST /v1/internal/providers/{providerId}/trust}
 * (FROZEN CONTRACT — WS-D/WS-F compile against this shape).
 *
 * @param targetLevel {@code ProviderTrustLevel} name (SELF_ASSERTED, DOCUMENT_SUPPORTED,
 *                    EMPLOYMENT_MATCHED, COUNCIL_VERIFIED, FULLY_LINKED_OR_ONBOARDED)
 * @param evidence    mandatory evidence backing the transition
 * @param channel     optional {@code OnboardingChannel} name (REGULATORY_A, WORKFORCE_B,
 *                    ORGANIZATION_C, SELF); recorded only if the provider has no channel yet
 */
public record TrustTransitionRequest(String targetLevel, TrustEvidenceDto evidence, String channel) {}
