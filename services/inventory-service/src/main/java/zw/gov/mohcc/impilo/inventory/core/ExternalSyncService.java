package zw.gov.mohcc.impilo.inventory.core;

import zw.gov.mohcc.impilo.inventory.domain.ExternalSystem;
import zw.gov.mohcc.impilo.inventory.domain.SyncEntityType;
import zw.gov.mohcc.impilo.inventory.domain.SyncStatus;
import zw.gov.mohcc.impilo.inventory.persistence.entity.ExternalSyncErrorEntity;
import zw.gov.mohcc.impilo.inventory.persistence.entity.ExternalSyncStateEntity;

import java.util.List;
import java.util.UUID;

/**
 * Dura external sync-state service for eLMIS/NatPharm exchange.
 *
 * <p>Tracks sync attempts separately from native stock truth. A failure here
 * never mutates the ledger or on-hand. State is explicit (PENDING/SYNCED/
 * FAILED/RETRY), auditable (per-attempt error history), and retryable (manual
 * replay back to PENDING). Automatic retries are capped; beyond the cap the
 * record is FAILED and awaits manual reconciliation.</p>
 *
 * <p>All operations are tenant-scoped via the trust context.</p>
 */
public interface ExternalSyncService {

    int MAX_AUTO_RETRIES = 3;

    /**
     * Queue a record for synchronisation (status PENDING).
     */
    ExternalSyncStateEntity enqueue(ExternalSystem system, SyncEntityType entityType,
                                    String entityRef, String payloadJson);

    /**
     * Mark a sync acknowledged by the external system.
     */
    ExternalSyncStateEntity markSynced(UUID syncId, String externalRef);

    /**
     * Record a failed attempt. Increments the attempt count, logs the error, and
     * sets RETRY (if attempts remain) or FAILED (cap reached).
     */
    ExternalSyncStateEntity markFailed(UUID syncId, String errorMessage);

    /**
     * Manually replay a FAILED/RETRY record back to PENDING.
     */
    ExternalSyncStateEntity retry(UUID syncId);

    ExternalSyncStateEntity getState(UUID syncId);

    List<ExternalSyncStateEntity> listByStatus(SyncStatus status);

    List<ExternalSyncErrorEntity> errorHistory(UUID syncId);
}
