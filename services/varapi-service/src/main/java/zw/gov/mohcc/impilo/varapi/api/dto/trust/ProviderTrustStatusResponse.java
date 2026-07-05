package zw.gov.mohcc.impilo.varapi.api.dto.trust;

import java.util.List;

/**
 * Response of {@code GET /v1/internal/providers/{providerId}/trust}
 * (FROZEN CONTRACT — WS-D/WS-F compile against this shape).
 *
 * @param trustLevel        current {@code ProviderTrustLevel} name (SELF_ASSERTED when unset)
 * @param registryStatus    current {@code ProviderRegistryStatus} name (nullable — the
 *                          registry does not invent certainty)
 * @param onboardingChannel {@code OnboardingChannel} name (nullable)
 * @param attempts          verification attempts, newest first, evidence refs masked
 */
public record ProviderTrustStatusResponse(String trustLevel,
                                          String registryStatus,
                                          String onboardingChannel,
                                          List<VerificationAttemptView> attempts) {}
