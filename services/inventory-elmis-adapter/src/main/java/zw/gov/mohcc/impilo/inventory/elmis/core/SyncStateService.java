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

    public SyncStateService(SyncStateRepository syncStateRepository) {
        this.syncStateRepository = syncStateRepository;
    }

    public List<SyncStateEntity> findAll() {
        return syncStateRepository.findAll();
    }

    public SyncStateEntity findById(Long id) {
        return syncStateRepository.findById(id)
                .orElseThrow(() -> new SyncNotFoundException(id));
    }

    /**
     * Triggers a new sync operation for the given tenant, facility, and sync type.
     * Creates a new sync state record in IN_PROGRESS status.
     */
    @Transactional
    public SyncStateEntity triggerSync(UUID tenantId, UUID facilityId, String syncType) {
        var entity = new SyncStateEntity();
        entity.setTenantId(tenantId);
        entity.setFacilityId(facilityId);
        entity.setSyncType(syncType);
        entity.setStatus("IN_PROGRESS");
        entity.setLastSyncAt(OffsetDateTime.now());
        return syncStateRepository.save(entity);
    }
}
