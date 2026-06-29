package zw.gov.mohcc.impilo.inventory.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.inventory.domain.ExternalSystem;
import zw.gov.mohcc.impilo.inventory.domain.SyncEntityType;
import zw.gov.mohcc.impilo.inventory.domain.SyncStatus;
import zw.gov.mohcc.impilo.inventory.persistence.entity.ExternalSyncErrorEntity;
import zw.gov.mohcc.impilo.inventory.persistence.entity.ExternalSyncStateEntity;
import zw.gov.mohcc.impilo.inventory.persistence.repository.ExternalSyncErrorRepository;
import zw.gov.mohcc.impilo.inventory.persistence.repository.ExternalSyncStateRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Implementation of the Dura external sync-state service.
 */
@Service
public class ExternalSyncServiceImpl implements ExternalSyncService {

    private static final Logger log = LoggerFactory.getLogger(ExternalSyncServiceImpl.class);

    private final ExternalSyncStateRepository stateRepository;
    private final ExternalSyncErrorRepository errorRepository;

    public ExternalSyncServiceImpl(ExternalSyncStateRepository stateRepository,
                                   ExternalSyncErrorRepository errorRepository) {
        this.stateRepository = stateRepository;
        this.errorRepository = errorRepository;
    }

    @Override
    @Transactional
    public ExternalSyncStateEntity enqueue(ExternalSystem system, SyncEntityType entityType,
                                           String entityRef, String payloadJson) {
        TrustContext ctx = TrustContextHolder.require();
        if (system == null || entityType == null) {
            throw new IllegalArgumentException("system and entityType are required");
        }
        if (entityRef == null || entityRef.isBlank()) {
            throw new IllegalArgumentException("entityRef is required");
        }
        ExternalSyncStateEntity state = new ExternalSyncStateEntity();
        state.setTenantId(ctx.tenantId());
        state.setExternalSystem(system);
        state.setEntityType(entityType);
        state.setEntityRef(entityRef);
        state.setPayload(payloadJson);
        state.setStatus(SyncStatus.PENDING);
        state = stateRepository.save(state);
        log.info("Sync enqueued: system={}, entity={}/{}", system, entityType, entityRef);
        return state;
    }

    @Override
    @Transactional
    public ExternalSyncStateEntity markSynced(UUID syncId, String externalRef) {
        ExternalSyncStateEntity state = loadOwned(syncId);
        state.setStatus(SyncStatus.SYNCED);
        state.setExternalRef(externalRef);
        state.setLastAttemptAt(OffsetDateTime.now());
        state.setSyncedAt(OffsetDateTime.now());
        state.setAttempts(state.getAttempts() + 1);
        state = stateRepository.save(state);
        log.info("Sync succeeded: syncId={}, externalRef={}", syncId, externalRef);
        return state;
    }

    @Override
    @Transactional
    public ExternalSyncStateEntity markFailed(UUID syncId, String errorMessage) {
        ExternalSyncStateEntity state = loadOwned(syncId);
        if (state.getStatus() == SyncStatus.SYNCED) {
            throw new IllegalStateException("Cannot fail an already-synced record: " + syncId);
        }
        int attempts = state.getAttempts() + 1;
        state.setAttempts(attempts);
        state.setLastError(errorMessage);
        state.setLastAttemptAt(OffsetDateTime.now());
        state.setStatus(attempts >= MAX_AUTO_RETRIES ? SyncStatus.FAILED : SyncStatus.RETRY);
        state = stateRepository.save(state);

        ExternalSyncErrorEntity error = new ExternalSyncErrorEntity();
        error.setTenantId(state.getTenantId());
        error.setSyncId(syncId);
        error.setAttemptNumber(attempts);
        error.setErrorMessage(errorMessage);
        errorRepository.save(error);

        log.warn("Sync failed: syncId={}, attempt={}, status={}", syncId, attempts, state.getStatus());
        return state;
    }

    @Override
    @Transactional
    public ExternalSyncStateEntity retry(UUID syncId) {
        ExternalSyncStateEntity state = loadOwned(syncId);
        if (state.getStatus() == SyncStatus.SYNCED) {
            throw new IllegalStateException("Cannot retry an already-synced record: " + syncId);
        }
        state.setStatus(SyncStatus.PENDING);
        state = stateRepository.save(state);
        log.info("Sync re-queued for replay: syncId={}", syncId);
        return state;
    }

    @Override
    @Transactional(readOnly = true)
    public ExternalSyncStateEntity getState(UUID syncId) {
        return loadOwned(syncId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExternalSyncStateEntity> listByStatus(SyncStatus status) {
        TrustContext ctx = TrustContextHolder.require();
        if (status != null) {
            return stateRepository.findByTenantIdAndStatusOrderByCreatedAtAsc(ctx.tenantId(), status);
        }
        return stateRepository.findByTenantIdOrderByCreatedAtDesc(ctx.tenantId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExternalSyncErrorEntity> errorHistory(UUID syncId) {
        loadOwned(syncId);
        return errorRepository.findBySyncIdOrderByCreatedAtAsc(syncId);
    }

    private ExternalSyncStateEntity loadOwned(UUID syncId) {
        TrustContext ctx = TrustContextHolder.require();
        ExternalSyncStateEntity state = stateRepository.findById(syncId)
                .orElseThrow(() -> new IllegalArgumentException("Sync record not found: " + syncId));
        if (state.getTenantId() == null || !state.getTenantId().equals(ctx.tenantId())) {
            throw new IllegalArgumentException("Sync record does not belong to the current tenant");
        }
        return state;
    }
}
