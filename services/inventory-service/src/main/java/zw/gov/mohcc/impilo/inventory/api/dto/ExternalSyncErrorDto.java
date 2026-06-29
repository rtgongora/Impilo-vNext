package zw.gov.mohcc.impilo.inventory.api.dto;

import zw.gov.mohcc.impilo.inventory.persistence.entity.ExternalSyncErrorEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

/** DTO for a sync error history entry. */
public record ExternalSyncErrorDto(
        UUID errorId,
        UUID syncId,
        int attemptNumber,
        String errorMessage,
        OffsetDateTime createdAt
) {
    public static ExternalSyncErrorDto from(ExternalSyncErrorEntity e) {
        return new ExternalSyncErrorDto(
                e.getErrorId(), e.getSyncId(), e.getAttemptNumber(), e.getErrorMessage(), e.getCreatedAt());
    }
}
