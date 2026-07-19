package zw.gov.mohcc.impilo.coverage.api.dto;

import jakarta.validation.constraints.NotBlank;
import zw.gov.mohcc.impilo.coverage.domain.PlanVersionEntity;
import zw.gov.mohcc.impilo.coverage.domain.ProductEntity;
import zw.gov.mohcc.impilo.coverage.domain.SchemeEntity;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Scheme -> Product -> PlanVersion request/response DTOs (spec §9), grouped for brevity.
 */
public final class HierarchyDtos {

    private HierarchyDtos() {}

    public record CreateSchemeRequest(
            @NotBlank String schemeCode,
            @NotBlank String name,
            @NotBlank String coverageType) {}

    public record SchemeResponse(
            UUID id, UUID payerRef, String schemeCode, String name,
            String coverageType, String status, LocalDate effectiveFrom) {
        public static SchemeResponse of(SchemeEntity s) {
            return new SchemeResponse(s.getId(), s.getPayerRef(), s.getSchemeCode(), s.getName(),
                    s.getCoverageType(), s.getStatus(), s.getEffectiveFrom());
        }
    }

    public record CreateProductRequest(
            @NotBlank String productCode,
            @NotBlank String name) {}

    public record ProductResponse(
            UUID id, UUID schemeRef, String productCode, String name, String status) {
        public static ProductResponse of(ProductEntity p) {
            return new ProductResponse(p.getId(), p.getSchemeRef(), p.getProductCode(), p.getName(), p.getStatus());
        }
    }

    public record CreatePlanVersionRequest(
            String currency,
            String approvalAuthority,
            Integer claimsSubmissionDeadlineDays,
            Integer appealDeadlineDays) {}

    public record PlanVersionResponse(
            UUID id, UUID productRef, int versionNumber, String status, String currency,
            LocalDate effectiveFrom, LocalDate effectiveTo, int claimsSubmissionDeadlineDays,
            int appealDeadlineDays, String approvalAuthority, OffsetDateTime publishedAt) {
        public static PlanVersionResponse of(PlanVersionEntity v) {
            return new PlanVersionResponse(v.getId(), v.getProductRef(), v.getVersionNumber(), v.getStatus(),
                    v.getCurrency(), v.getEffectiveFrom(), v.getEffectiveTo(), v.getClaimsSubmissionDeadlineDays(),
                    v.getAppealDeadlineDays(), v.getApprovalAuthority(), v.getPublishedAt());
        }
    }
}
