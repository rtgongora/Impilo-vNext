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
        boolean active,
        boolean hasValidLicense,
        boolean picEligible,
        // Platform participation: has the practitioner claimed their record? Registration on
        // the HPA roll makes them findable and verifiable; this is what makes them bookable.
        // Cross-service gates (booking, commerce, PIC) must read this rather than inferring
        // participation from status — INACTIVE only ever meant "has not opted in yet".
        boolean claimed,
        LocalDate licenseValidTo
) {}

