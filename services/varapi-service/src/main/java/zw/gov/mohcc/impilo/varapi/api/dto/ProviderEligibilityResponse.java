package zw.gov.mohcc.impilo.varapi.api.dto;

import java.time.LocalDate;

public record ProviderEligibilityResponse(
        Long providerId,
        String providerPublicId,
        boolean eligible,
        String eligibilityStatus,
        boolean hasValidLicense,
        boolean hasActivePractisingCertificate,
        boolean hasNoOverdueCompliance,
        boolean hasActiveAffiliation,
        boolean canServeAsPic,
        LocalDate licenseExpiryDate,
        LocalDate certificateExpiryDate,
        String[] blockReasons
) {
    public static ProviderEligibilityResponse ineligible(Long providerId, String providerPublicId, String[] reasons) {
        return new ProviderEligibilityResponse(
                providerId,
                providerPublicId,
                false,
                "INELIGIBLE",
                false, false, false, false, false,
                null, null,
                reasons
        );
    }

    public static ProviderEligibilityResponse eligible(Long providerId, String providerPublicId,
            LocalDate licenseExpiry, LocalDate certificateExpiry) {
        return new ProviderEligibilityResponse(
                providerId,
                providerPublicId,
                true,
                "ELIGIBLE",
                true, true, true, true, true,
                licenseExpiry, certificateExpiry,
                new String[0]
        );
    }
}