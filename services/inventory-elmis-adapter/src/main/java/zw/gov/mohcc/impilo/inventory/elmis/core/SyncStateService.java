package zw.gov.mohcc.impilo.inventory.elmis.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.inventory.elmis.persistence.entity.SyncStateEntity;
import zw.gov.mohcc.impilo.inventory.elmis.persistence.repository.SyncStateRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Core service for managing eLMIS sync state lifecycle.
 *
 * <p>Tracks the status, timing, and record counts for each
 * sync operation between a facility and the national eLMIS.</p>
 */
@Service
@Transactional(readOnly = true)
public class SyncStateService {

    private final SyncStateRepository syncStateRepository;
    private final ElmisSyncConnector connector;

    public SyncStateService(SyncStateRepository syncStateRepository, ElmisSyncConnector connector) {
        this.syncStateRepository = syncStateRepository;
        this.connector = connector;
    }

    public List<SyncStateEntity> findAll() {
        return syncStateRepository.findAll();
    }

    public SyncStateEntity findById(Long id) {
        return syncStateRepository.findById(id)
                .orElseThrow(() -> new SyncNotFoundException(id));
    }

    /**
     * Triggers a sync for the given tenant, facility, and sync type, then drives it
     * to a terminal state via the {@link ElmisSyncConnector}. The sync ends COMPLETED
     * (with the synced record count) or FAILED (with a reason) — it is never left
     * hanging IN_PROGRESS, and when no real eLMIS endpoint is configured it fails
     * closed as NOT_LIVE rather than fabricating success (G023).
     */
    @Transactional
    public SyncStateEntity triggerSync(UUID tenantId, UUID facilityId, String syncType) {
        var entity = new SyncStateEntity();
        entity.setTenantId(tenantId);
        entity.setFacilityId(facilityId);
        entity.setSyncType(syncType);
        entity.setStatus("IN_PROGRESS");
        entity.setLastSyncAt(OffsetDateTime.now());

        SyncResult result = connector.sync(entity);
        entity.setStatus(result.status());
        entity.setRecordsSynced(result.recordsSynced());
        entity.setErrorMessage(result.message());
        entity.setLastSyncAt(OffsetDateTime.now());

        return syncStateRepository.save(entity);
    }
}
