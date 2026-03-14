package zw.gov.mohcc.impilo.offlinesync.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.offlinesync.api.dto.ResolveConflictRequest;
import zw.gov.mohcc.impilo.offlinesync.domain.SyncStatus;
import zw.gov.mohcc.impilo.offlinesync.persistence.entity.ConflictQueueEntity;
import zw.gov.mohcc.impilo.offlinesync.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.offlinesync.persistence.entity.SyncPackEntity;
import zw.gov.mohcc.impilo.offlinesync.persistence.repository.ConflictQueueRepository;
import zw.gov.mohcc.impilo.offlinesync.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.offlinesync.persistence.repository.SyncPackRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Core service for conflict queue management: listing unresolved conflicts
 * and resolving them with a chosen strategy.
 */
@Service
public class ConflictService {

    private static final Logger log = LoggerFactory.getLogger(ConflictService.class);

    private final ConflictQueueRepository conflictQueueRepository;
    private final SyncPackRepository syncPackRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public ConflictService(ConflictQueueRepository conflictQueueRepository,
                           SyncPackRepository syncPackRepository,
                           EventOutboxRepository outboxRepository,
                           ObjectMapper objectMapper) {
        this.conflictQueueRepository = conflictQueueRepository;
        this.syncPackRepository = syncPackRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Lists all conflicts (unresolved by default).
     */
    public List<ConflictQueueEntity> listConflicts() {
        return conflictQueueRepository.findByResolutionIsNull();
    }

    /**
     * Resolves a conflict with the given resolution strategy.
     */
    @Transactional
    public ConflictQueueEntity resolveConflict(Long conflictId, ResolveConflictRequest request) {
        ConflictQueueEntity conflict = conflictQueueRepository.findById(conflictId)
                .orElseThrow(() -> new SyncPackNotFoundException("Conflict not found: " + conflictId));

        if (conflict.getResolution() != null) {
            throw new IllegalStateException("Conflict already resolved: " + conflictId);
        }

        conflict.setResolution(request.getResolution().name());
        conflict.setResolvedAt(OffsetDateTime.now());
        conflict = conflictQueueRepository.save(conflict);

        // Check if all conflicts for this sync pack are resolved
        List<ConflictQueueEntity> remaining = conflictQueueRepository
                .findBySyncPackId(conflict.getSyncPackId())
                .stream()
                .filter(c -> c.getResolution() == null)
                .toList();

        if (remaining.isEmpty()) {
            syncPackRepository.findById(conflict.getSyncPackId()).ifPresent(syncPack -> {
                if (SyncStatus.CONFLICT.name().equals(syncPack.getStatus())) {
                    syncPack.setStatus(SyncStatus.PENDING.name());
                    syncPackRepository.save(syncPack);
                    log.info("All conflicts resolved for sync pack {}, status reset to PENDING",
                            syncPack.getId());
                }
            });
        }

        appendOutboxEvent(conflict.getTenantId(), "CONFLICT_RESOLVED", buildConflictPayload(conflict));

        log.info("Conflict resolved: id={}, resolution={}", conflictId, request.getResolution());

        return conflict;
    }

    private Map<String, Object> buildConflictPayload(ConflictQueueEntity conflict) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", conflict.getId());
        payload.put("tenantId", conflict.getTenantId().toString());
        payload.put("syncPackId", conflict.getSyncPackId());
        payload.put("conflictType", conflict.getConflictType());
        payload.put("resolution", conflict.getResolution());
        payload.put("resolvedAt", conflict.getResolvedAt() != null ? conflict.getResolvedAt().toString() : null);
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
