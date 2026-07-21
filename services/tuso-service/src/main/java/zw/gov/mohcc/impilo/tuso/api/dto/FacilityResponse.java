package zw.gov.mohcc.impilo.tuso.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record FacilityResponse(
        Long id,
        String facilityUuid,
        String name,
        String facilityCode,
        String facilityType,
        String province,
        String district,
        BigDecimal latitude,
        BigDecimal longitude,
        String status,
        String operationalStatus,
        String ownership,
        String level,
        String facilityCategory,
        OperatingModelDetail operatingModel,
        Long parentId,
        String parentName,
        String description,
        LocalDate openedDate,
        LocalDate closedDate,
        String closeReason,
        Long mergedIntoId,
        Integer version,
        List<FacilityIdentifierDto> identifiers,
        List<FacilityContactDto> contacts,
        GeoDetail geo,
        List<CapabilityDetail> capabilities,
        ReadinessDetail readiness,
        Instant createdAt,
        Instant updatedAt,
        String createdBy,
        String updatedBy,
        // HPA enrichment disclosure (public profile) — honest source/verification/completeness.
        // Null for non-regulator-listed records; the citizen surface renders them defensively.
        String sourceSystem,
        String sourceEffectiveDate,
        String verificationStatus,
        Boolean profileIncomplete,
        Boolean siteUnresolved,
        // Read-only, Rito-sourced verified facility-experience summary (RW8). TUSO composes
        // and displays it; it never owns or stores ratings. Null when Rito is disabled/unavailable.
        FacilityExperienceSummary experience
) {
    /** Verified facility-experience summary composed from Rito. Source is always "Rito". */
    public record FacilityExperienceSummary(String source, String reportingPeriod,
                                            int verifiedInteractionTotal,
                                            java.util.List<ExperienceDomain> domains) {}

    public record ExperienceDomain(String domain, java.math.BigDecimal verifiedMeanScore, int verifiedCount) {}

    public record OperatingModelDetail(
            String facilityTier,
            String deploymentMode,
            String continuityClass,
            String workflowArchetype
    ) {}

    public record GeoDetail(
            String addressLine1,
            String addressLine2,
            String city,
            String province,
            String district,
            String ward,
            String postalCode,
            String country,
            BigDecimal altitudeM,
            String catchmentArea
    ) {}

    public record CapabilityDetail(
            Long id,
            String capabilityCode,
            String capabilityType,
            String name,
            boolean ziboValidated,
            boolean active,
            Map<String, Object> operatingHours,
            Map<String, Object> metadata
    ) {}

    public record ReadinessDetail(
            String connectivity,
            String powerSource,
            boolean powerBackup,
            int deviceCount,
            boolean ehrReady,
            Map<String, Object> complianceFlags,
            Instant assessedAt,
            String assessedBy
    ) {}
}
