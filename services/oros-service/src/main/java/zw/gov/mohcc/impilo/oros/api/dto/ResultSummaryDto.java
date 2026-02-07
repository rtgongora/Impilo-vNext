package zw.gov.mohcc.impilo.oros.api.dto;

import zw.gov.mohcc.impilo.oros.domain.ResultKind;
import zw.gov.mohcc.impilo.oros.persistence.entity.ResultEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Summary DTO for result data in API responses.
 */
public record ResultSummaryDto(
        UUID resultId,
        String orderId,
        ResultKind kind,
        String summary,
        String ziboResultCodes,
        String docIds,
        boolean isCritical,
        String reportedBy,
        OffsetDateTime createdAt
) {
    /**
     * Factory method to create a DTO from a result entity.
     */
    public static ResultSummaryDto from(ResultEntity entity) {
        return new ResultSummaryDto(
                entity.getResultId(),
                entity.getOrderId(),
                entity.getKind(),
                entity.getSummary(),
                entity.getZiboResultCodes(),
                entity.getDocIds(),
                entity.isCritical(),
                entity.getReportedBy(),
                entity.getCreatedAt()
        );
    }
}
