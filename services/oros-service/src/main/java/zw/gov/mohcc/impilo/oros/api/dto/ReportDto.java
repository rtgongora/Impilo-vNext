package zw.gov.mohcc.impilo.oros.api.dto;

import zw.gov.mohcc.impilo.oros.domain.ResultKind;
import zw.gov.mohcc.impilo.oros.domain.ResultStatus;
import zw.gov.mohcc.impilo.oros.persistence.entity.ResultEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO for a versioned diagnostic report/result, including its lifecycle status,
 * version chain pointer, critical flag, and release/acknowledge state.
 */
public record ReportDto(
        UUID resultId,
        String orderId,
        ResultKind kind,
        ResultStatus reportStatus,
        int version,
        UUID supersedesResultId,
        String summary,
        String impression,
        String recommendations,
        boolean critical,
        String criticalReason,
        String reportedBy,
        String validatedBy,
        OffsetDateTime releasedAt,
        OffsetDateTime acknowledgedAt,
        String acknowledgedBy,
        OffsetDateTime createdAt
) {
    public static ReportDto from(ResultEntity r) {
        return new ReportDto(
                r.getResultId(), r.getOrderId(), r.getKind(), r.getReportStatus(), r.getVersion(),
                r.getSupersedesResultId(), r.getSummary(), r.getImpression(), r.getRecommendations(),
                r.isCritical(), r.getCriticalReason(), r.getReportedBy(), r.getValidatedBy(),
                r.getReleasedAt(), r.getAcknowledgedAt(), r.getAcknowledgedBy(), r.getCreatedAt());
    }
}
