package zw.gov.mohcc.impilo.inventory.api.dto;

import zw.gov.mohcc.impilo.inventory.domain.ExternalSystem;
import zw.gov.mohcc.impilo.inventory.domain.SyncEntityType;
import zw.gov.mohcc.impilo.inventory.domain.SyncStatus;
import zw.gov.mohcc.impilo.inventory.persistence.entity.ExternalSyncStateEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

/** DTO for an external sync-state record. */
public record ExternalSyncStateDto(
        UUID syncId,
        ExternalSystem externalSystem,
        SyncEntityType entityType,
        String entityRef,
        String externalRef,
        SyncStatus status,
        int attempts,
        String lastError,
        OffsetDateTime createdAt,
        OffsetDateTime lastAttemptAt,
        OffsetDateTime syncedAt
) {
    public static ExternalSyncStateDto from(ExternalSyncStateEntity e) {
        return new ExternalSyncStateDto(
                e.getSyncId(), e.getExternalSystem(), e.getEntityType(), e.getEntityRef(),
                e.getExternalRef(), e.getStatus(), e.getAttempts(), e.getLastError(),
                e.getCreatedAt(), e.getLastAttemptAt(), e.getSyncedAt());
    }
}
