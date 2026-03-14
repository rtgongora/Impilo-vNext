package zw.gov.mohcc.impilo.offlinesync.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.offlinesync.api.dto.CreateSyncPackRequest;
import zw.gov.mohcc.impilo.offlinesync.domain.SyncStatus;
import zw.gov.mohcc.impilo.offlinesync.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.offlinesync.persistence.entity.SyncPackEntity;
import zw.gov.mohcc.impilo.offlinesync.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.offlinesync.persistence.repository.SyncPackRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Core service for sync pack management: creation, listing, and replay.
 * Each state transition appends an event to the outbox for reliable Kafka publishing.
 */
@Service
public class SyncPackService {

    private static final Logger log = LoggerFactory.getLogger(SyncPackService.class);

    private final SyncPackRepository syncPackRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public SyncPackService(SyncPackRepository syncPackRepository,
                           EventOutboxRepository outboxRepository,
                           ObjectMapper objectMapper) {
        this.syncPackRepository = syncPackRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Lists all sync packs.
     */
    public List<SyncPackEntity> listSyncPacks() {
        return syncPackRepository.findAll();
    }

    /**
     * Retrieves a single sync pack by ID.
     */
    public SyncPackEntity getSyncPack(Long id) {
        return syncPackRepository.findById(id)
                .orElseThrow(() -> new SyncPackNotFoundException("Sync pack not found: " + id));
    }

    /**
     * Creates a new sync pack and writes a SYNC_PACK_CREATED outbox event.
     */
    @Transactional
    public SyncPackEntity createSyncPack(CreateSyncPackRequest request) {
        SyncPackEntity entity = new SyncPackEntity();
        entity.setTenantId(request.getTenantId());
        entity.setFacilityId(request.getFacilityId());
        entity.setDeviceId(request.getDeviceId());
        entity.setPayload(request.getPayload());
        entity.setStatus(SyncStatus.PENDING.name());
        entity.setPackVersion(1L);
        entity.setCreatedAt(OffsetDateTime.now());

        entity = syncPackRepository.save(entity);

        appendOutboxEvent(entity.getTenantId(), "SYNC_PACK_CREATED", buildSyncPackPayload(entity));

        log.info("Sync pack created: id={}, facility={}, device={}",
                entity.getId(), entity.getFacilityId(), entity.getDeviceId());

        return entity;
    }

    /**
     * Replays a sync pack by transitioning it from PENDING/FAILED/CONFLICT to SYNCING,
     * then to SYNCED. Writes a SYNC_PACK_REPLAYED outbox event.
     */
    @Transactional
    public SyncPackEntity replaySyncPack(Long id) {
        SyncPackEntity entity = getSyncPack(id);

        String currentStatus = entity.getStatus();
        if (SyncStatus.SYNCED.name().equals(currentStatus)) {
            throw new IllegalStateException("Sync pack already synced: " + id);
        }
        if (SyncStatus.SYNCING.name().equals(currentStatus)) {
            throw new IllegalStateException("Sync pack is already being synced: " + id);
        }

        entity.setStatus(SyncStatus.SYNCING.name());
        syncPackRepository.save(entity);

        // Simulate replay: mark as SYNCED
        entity.setStatus(SyncStatus.SYNCED.name());
        entity.setSyncedAt(OffsetDateTime.now());
        entity = syncPackRepository.save(entity);

        appendOutboxEvent(entity.getTenantId(), "SYNC_PACK_REPLAYED", buildSyncPackPayload(entity));

        log.info("Sync pack replayed: id={}, status={}", entity.getId(), entity.getStatus());

        return entity;
    }

    private Map<String, Object> buildSyncPackPayload(SyncPackEntity entity) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", entity.getId());
        payload.put("tenantId", entity.getTenantId().toString());
        payload.put("facilityId", entity.getFacilityId().toString());
        payload.put("deviceId", entity.getDeviceId());
        payload.put("packVersion", entity.getPackVersion());
        payload.put("status", entity.getStatus());
        payload.put("createdAt", entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null);
        payload.put("syncedAt", entity.getSyncedAt() != null ? entity.getSyncedAt().toString() : null);
        return payload;
    }

    private void appendOutboxEvent(UUID tenantId, String eventType, Map<String, Object> payload) {
        EventOutboxEntity outbox = new EventOutboxEntity();
        outbox.setTenantId(tenantId);
        outbox.setPodId(System.getenv("HOSTNAME") != null ? System.getenv("HOSTNAME") : "local");
        outbox.setCorrelationId(UUID.randomUUID().toString());
        outbox.setRequestId(UUID.randomUUID().toString());
        outbox.setEventType(eventType);
        try {
            outbox.setPayload(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize outbox payload", e);
        }
        outboxRepository.save(outbox);
    }
}
