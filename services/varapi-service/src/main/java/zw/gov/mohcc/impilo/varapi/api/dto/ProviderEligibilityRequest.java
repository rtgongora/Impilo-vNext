package zw.gov.mohcc.impilo.varapi.api.dto;

public record ProviderEligibilityRequest(
        Long providerId,
        String providerPublicId,
        Long facilityId,
        String contextType,
        boolean checkPicEligibility,
        boolean checkLicenseStatus,
        boolean checkCertificateStatus,
        boolean checkComplianceStatus,
        boolean checkAffiliationStatus
) {
    public static ProviderEligibilityRequest fullCheck(Long providerId, Long facilityId) {
        return new ProviderEligibilityRequest(
                providerId,
                null,
                facilityId,
                null,
                true, true, true, true, true
        );
    }

    public static ProviderEligibilityRequest byPublicId(String providerPublicId, Long facilityId) {
        return new ProviderEligibilityRequest(
                null,
                providerPublicId,
                facilityId,
                null,
                true, true, true, true, true
        );
    }
}