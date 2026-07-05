package zw.gov.mohcc.impilo.tuso.api.dto;

import jakarta.validation.constraints.NotNull;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityLegitimacySource;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityLegitimacyStatus;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityRegulatoryStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** DTOs for the honest per-source facility legitimacy model (WS-E). */
public final class FacilitySourceLegitimacyDtos {

    private FacilitySourceLegitimacyDtos() {}

    /**
     * Upsert body for {@code PUT /v1/internal/facilities/{facilityId}/source-legitimacy/{source}}.
     *
     * <p>{@code allowedOnPlatform} is nullable on the wire: for ordinary statuses a missing value
     * defaults (honestly) to {@code false}; for {@link FacilityLegitimacyStatus#GOVERNMENT_OPERATIONAL_EXCEPTION}
     * it MUST be supplied explicitly together with a non-blank {@code reason}.</p>
     */
    public record UpsertSourceLegitimacyRequest(
            @NotNull FacilityLegitimacyStatus status,
            Instant asOf,
            String evidenceRef,
            Boolean allowedOnPlatform,
            String reason
    ) {}

    /** Current verdict of one source about one facility. */
    public record SourceLegitimacyView(
            FacilityLegitimacySource source,
            FacilityLegitimacyStatus status,
            Instant asOf,
            String evidenceRef,
            boolean allowedOnPlatform,
            String reason,
            String recordedBy,
            Instant createdAt,
            Instant updatedAt
    ) {}

    /** Response for {@code GET /v1/facilities/{facilityId}/source-legitimacy}. */
    public record SourceLegitimacyListResponse(
            UUID facilityId,
            List<SourceLegitimacyView> sources
    ) {}

    /** Read-only summary of an existing regulatory certificate (legal/HPA carrier). */
    public record CertificateSummary(
            UUID certificateId,
            String certificateType,
            String certificateNumber,
            String status,
            LocalDate issueDate,
            LocalDate expiryDate,
            String issuedUnderAuthority
    ) {}

    /**
     * Composite, side-by-side truth for a facility: the existing regulatory lifecycle status, the
     * certificate summary, and every per-source legitimacy verdict — plus the derived platform
     * access verdict ({@code platformAccessAllowed} = no source denies AND at least one allows).
     */
    public record FacilityStatusCompositeResponse(
            UUID facilityId,
            String facilityCode,
            String facilityName,
            FacilityRegulatoryStatus regulatoryStatus,
            Instant regulatoryStatusUpdatedAt,
            List<CertificateSummary> certificates,
            List<SourceLegitimacyView> sourceLegitimacy,
            boolean platformAccessAllowed,
            List<String> reasons
    ) {}
}
