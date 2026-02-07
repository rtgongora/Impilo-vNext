package zw.gov.mohcc.impilo.oros.api.dto;

import zw.gov.mohcc.impilo.oros.domain.ReconcileStatus;
import zw.gov.mohcc.impilo.oros.persistence.entity.ReconcileQueueEntity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Summary DTO for reconciliation queue entries in API responses.
 */
public record ReconcileSummaryDto(
        UUID recId,
        String orderId,
        String externalKey,
        String externalSystem,
        String patientCpid,
        BigDecimal confidence,
        ReconcileStatus status,
        String opsNotes,
        OffsetDateTime matchedAt,
        String resolvedBy,
        OffsetDateTime createdAt
) {
    /**
     * Factory method to create a DTO from a reconcile queue entity.
     */
    public static ReconcileSummaryDto from(ReconcileQueueEntity entity) {
        return new ReconcileSummaryDto(
                entity.getRecId(),
                entity.getOrderId(),
                entity.getExternalKey(),
                entity.getExternalSystem(),
                entity.getPatientCpid(),
                entity.getConfidence(),
                entity.getStatus(),
                entity.getOpsNotes(),
                entity.getMatchedAt(),
                entity.getResolvedBy(),
                entity.getCreatedAt()
        );
    }
}
