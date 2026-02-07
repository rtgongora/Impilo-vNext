package zw.gov.mohcc.impilo.pharmacy.api.dto;

import zw.gov.mohcc.impilo.pharmacy.domain.ReconcileStatus;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.ReconcileQueueEntity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Summary DTO for stock reconciliation queue entries.
 * Uses a static factory method to map from the entity.
 */
public record ReconcileSummaryDto(
        UUID recId,
        UUID facilityId,
        String itemCode,
        Integer internalQty,
        Integer externalQty,
        BigDecimal confidence,
        ReconcileStatus status,
        String opsNotes,
        OffsetDateTime createdAt
) {
    /**
     * Factory method to create a reconcile summary DTO from the entity.
     */
    public static ReconcileSummaryDto from(ReconcileQueueEntity entity) {
        return new ReconcileSummaryDto(
                entity.getRecId(),
                entity.getFacilityId(),
                entity.getItemCode(),
                entity.getInternalQty(),
                entity.getExternalQty(),
                entity.getConfidence(),
                entity.getStatus(),
                entity.getOpsNotes(),
                entity.getCreatedAt()
        );
    }
}
