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
     * Replay is <b>not implemented</b> and therefore refuses.
     *
     * <p>Applying a pack means writing its payload into the systems of record it came from
     * (PCT, VITO, OROS…). Nothing in this service does that: there is no downstream client, no
     * consumer, and no publisher for the event it used to emit. The previous implementation
     * flipped the row {@code PENDING → SYNCING → SYNCED}, stamped {@code syncedAt} and returned
     * 200 — all of it under {@code // Simulate replay: mark as SYNCED}.</p>
     *
     * <p>The pack is left exactly as it was, so it stays outstanding rather than being silently
     * retired. See {@link SyncReplayNotImplementedException} for why a false success here is a
     * data-loss path and not just a cosmetic lie.</p>
     */
    @Transactional(readOnly = true)
    public SyncPackEntity replaySyncPack(Long id) {
        // Resolve first: an unknown id is still a 404, not a 501.
        SyncPackEntity entity = getSyncPack(id);
        log.warn("Sync pack replay REFUSED (not implemented): id={}, status={}",
                entity.getId(), entity.getStatus());
        throw new SyncReplayNotImplementedException(id);
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
