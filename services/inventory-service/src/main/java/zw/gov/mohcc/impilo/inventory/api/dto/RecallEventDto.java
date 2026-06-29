package zw.gov.mohcc.impilo.inventory.api.dto;

import zw.gov.mohcc.impilo.inventory.domain.RecallSeverity;
import zw.gov.mohcc.impilo.inventory.domain.RecallSource;
import zw.gov.mohcc.impilo.inventory.domain.RecallStatus;
import zw.gov.mohcc.impilo.inventory.persistence.entity.RecallEventEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * DTO for a Dura recall event.
 */
public record RecallEventDto(
        UUID recallId,
        String recallReference,
        String itemCode,
        String batchNumber,
        RecallSource source,
        RecallSeverity severity,
        String reason,
        RecallStatus status,
        int affectedBatches,
        String initiatedBy,
        String closureNote,
        OffsetDateTime createdAt,
        OffsetDateTime closedAt
) {
    public static RecallEventDto from(RecallEventEntity e) {
        return new RecallEventDto(
                e.getRecallId(),
                e.getRecallReference(),
                e.getItemCode(),
                e.getBatchNumber(),
                e.getSource(),
                e.getSeverity(),
                e.getReason(),
                e.getStatus(),
                e.getAffectedBatches(),
                e.getInitiatedBy(),
                e.getClosureNote(),
                e.getCreatedAt(),
                e.getClosedAt()
        );
    }
}
