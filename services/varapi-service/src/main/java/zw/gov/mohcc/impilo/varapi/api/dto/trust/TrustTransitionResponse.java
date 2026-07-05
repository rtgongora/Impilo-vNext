package zw.gov.mohcc.impilo.varapi.api.dto.trust;

/**
 * Response of {@code POST /v1/internal/providers/{providerId}/trust}
 * (FROZEN CONTRACT — WS-D/WS-F compile against this shape).
 *
 * @param providerPublicId MASKED platform Provider ID (first 4 chars + "***") — the
 *                         confidential full value never leaves the trust API
 * @param trustLevel       resulting {@code ProviderTrustLevel} name
 * @param registryStatus   resulting {@code ProviderRegistryStatus} name
 */
public record TrustTransitionResponse(String providerPublicId, String trustLevel, String registryStatus) {}
