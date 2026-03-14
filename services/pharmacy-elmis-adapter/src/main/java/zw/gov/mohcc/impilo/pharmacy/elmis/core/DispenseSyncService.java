package zw.gov.mohcc.impilo.pharmacy.elmis.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pharmacy.elmis.domain.SyncStatus;
import zw.gov.mohcc.impilo.pharmacy.elmis.persistence.entity.DispenseSyncEntity;
import zw.gov.mohcc.impilo.pharmacy.elmis.persistence.repository.DispenseSyncRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing dispense synchronization runs between
 * the pharmacy service and the external eLMIS.
 */
@Service
public class DispenseSyncService {

    private final DispenseSyncRepository repository;

    public DispenseSyncService(DispenseSyncRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<DispenseSyncEntity> findAll() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public DispenseSyncEntity findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new SyncNotFoundException(id));
    }

    @Transactional
    public DispenseSyncEntity triggerSync(UUID tenantId, UUID facilityId, String syncType) {
        var entity = new DispenseSyncEntity();
        entity.setTenantId(tenantId);
        entity.setFacilityId(facilityId);
        entity.setSyncType(syncType);
        entity.setStatus(SyncStatus.RUNNING);
        entity.setLastSyncAt(OffsetDateTime.now());
        return repository.save(entity);
    }
}
