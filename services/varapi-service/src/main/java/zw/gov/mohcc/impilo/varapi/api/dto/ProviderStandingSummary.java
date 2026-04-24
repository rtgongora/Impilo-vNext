package zw.gov.mohcc.impilo.varapi.api.dto;

import java.time.LocalDate;

/**
 * Lightweight provider legitimacy summary for cross-service gating.
 * Varapi remains the source-of-truth for provider standing/licensure.
 */
public record ProviderStandingSummary(
        String providerPublicId,
        String status,
        String lifecycleStatus,
        String licenceStatus,
        String professionalStandingStatus,
        boolean active,
        boolean hasValidLicense,
        boolean picEligible,
        LocalDate licenseValidTo
) {}

