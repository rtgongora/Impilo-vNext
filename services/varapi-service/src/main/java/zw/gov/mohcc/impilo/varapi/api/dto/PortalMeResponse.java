package zw.gov.mohcc.impilo.varapi.api.dto;

public record PortalMeResponse(
        String providerPublicId,
        String givenName,
        String familyName,
        String title,
        String profession,
        String cadre,
        String status,
        String currentLicenseStatus,
        java.time.LocalDate licenseValidUntil,
        int activeFacilityCount,
        int activePrivilegeCount,
        int currentCpdPoints,
        int requiredCpdPoints
) {}
