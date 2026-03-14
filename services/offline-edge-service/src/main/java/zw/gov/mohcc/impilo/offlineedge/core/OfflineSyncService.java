package zw.gov.mohcc.impilo.offlineedge.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.offline.sdk.entitlement.OfflineEntitlement;
import zw.gov.mohcc.impilo.offlineedge.api.dto.SyncRequest;
import zw.gov.mohcc.impilo.offlineedge.api.dto.SyncResponse;
import zw.gov.mohcc.impilo.offlineedge.domain.CapturedActionEntity;
import zw.gov.mohcc.impilo.offlineedge.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.offlineedge.domain.ReconciliationBatchEntity;
import zw.gov.mohcc.impilo.offlineedge.repository.CapturedActionRepository;
import zw.gov.mohcc.impilo.offlineedge.repository.OutboxEventRepository;
import zw.gov.mohcc.impilo.offlineedge.repository.ReconciliationBatchRepository;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Service for processing offline sync batches.
 *
 * <p>When an edge device comes online, it pushes all queued actions as a sync batch.
 * Each action is validated, stored with audit, and an outbox event is written
 * using {@code EventEnvelope} format with the original correlation_id preserved.</p>
 *
 * <p>The batch is tracked via {@link ReconciliationBatchEntity} for observability.</p>
 */
@Service
public class OfflineSyncService {

    private static final Logger log = LoggerFactory.getLogger(OfflineSyncService.class);
    private final CapturedActionRepository actionRepository;
    private final OutboxEventRepository outboxRepository;
    private final ReconciliationBatchRepository batchRepository;
    private final ObjectMapper objectMapper;

    public OfflineSyncService(CapturedActionRepository actionRepository,
                               OutboxEventRepository outboxRepository,
                               ReconciliationBatchRepository batchRepository,
                               ObjectMapper objectMapper) {
        this.actionRepository = actionRepository;
        this.outboxRepository = outboxRepository;
        this.batchRepository = batchRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Process an incoming sync batch from an edge device.
     *
     * <p>Each action in the batch is stored individually with audit events.
     * The method is replay-safe: duplicate entryIds are detected via idempotency.</p>
     *
     * @param entitlement verified entitlement from JWT
     * @param request     the sync batch request
     * @return sync response with per-action results
     */
    @Transactional
    public SyncResponse processSyncBatch(OfflineEntitlement entitlement, SyncRequest request) {
        UUID batchId = request.batchId();
        int totalActions = request.actions().size();

        // Create reconciliation batch tracker
        ReconciliationBatchEntity batch = new ReconciliationBatchEntity(
                batchId, entitlement.tenantId(), entitlement.tokenId(), totalActions);
        batch.setStatus("SYNCING");
        batch.setStartedAt(OffsetDateTime.now());
        batchRepository.save(batch);

        List<SyncResponse.SyncActionResult> results = new ArrayList<>();
        int accepted = 0;
        int rejected = 0;

        for (SyncRequest.SyncActionEntry entry : request.actions()) {
            try {
                UUID actionId = processSingleAction(entitlement, entry, batchId);
                results.add(new SyncResponse.SyncActionResult(
                        entry.entryId(), actionId, "QUEUED", null));
                accepted++;
            } catch (Exception e) {
                log.warn("Sync action rejected [entryId={}, reason={}]", entry.entryId(), e.getMessage());
                results.add(new SyncResponse.SyncActionResult(
                        entry.entryId(), null, "REJECTED", e.getMessage()));
                rejected++;
            }
        }

        // Update batch status
        batch.setReplayedCount(accepted);
        batch.setFailedCount(rejected);
        batch.setStatus(rejected == 0 ? "SYNCED" : "PARTIAL");
        batch.setCompletedAt(OffsetDateTime.now());
        batchRepository.save(batch);

        // Emit batch-level sync event
        emitSyncBatchEvent(entitlement, batchId, accepted, rejected, totalActions);

        log.info("Sync batch [batchId={}, accepted={}, rejected={}, total={}]",
                batchId, accepted, rejected, totalActions);

        return new SyncResponse(batchId, batch.getStatus(), totalActions, accepted, rejected, results);
    }

    private UUID processSingleAction(OfflineEntitlement entitlement,
                                      SyncRequest.SyncActionEntry entry,
                                      UUID batchId) {
        UUID actionId = entry.entryId() != null ? entry.entryId() : UUID.randomUUID();
        OffsetDateTime capturedAt = OffsetDateTime.parse(entry.capturedAt());
        String payloadJson = toJson(entry.payload());
        String correlationId = entry.correlationId() != null
                ? entry.correlationId() : UUID.randomUUID().toString();

        // Store the action
        CapturedActionEntity action = new CapturedActionEntity(
                actionId,
                entitlement.tokenId(),
                entitlement.tenantId(),
                entitlement.actorId(),
                entry.patientRef(),
                entry.actionType(),
                payloadJson,
                capturedAt,
                entry.deviceId() != null ? entry.deviceId() : "unknown",
                entry.sequenceNum() > 0 ? entry.sequenceNum() : 1
        );
        actionRepository.save(action);

        // Emit per-action outbox event (preserves original correlation_id)
        OutboxEventEntity outbox = new OutboxEventEntity();
        outbox.setAggregateType("OfflineAction");
        outbox.setAggregateId(actionId.toString());
        outbox.setEventType("impilo.offline.action.synced.v1");
        outbox.setCorrelationId(parseUuid(correlationId));
        outbox.setCausationId(parseUuid(correlationId));
        outbox.setIdempotencyKey(entry.idempotencyKey());
        outbox.setTenantId(entitlement.tenantId());
        outbox.setPodId(entitlement.facilityId() != null
                ? entitlement.facilityId().toString() : "national-spine");
        outbox.setSubjectId(entry.patientRef());
        outbox.setSubjectType("OfflineAction");
        outbox.setOccurredAt(capturedAt);

        Map<String, Object> eventPayload = new LinkedHashMap<>();
        eventPayload.put("action_id", actionId.toString());
        eventPayload.put("batch_id", batchId.toString());
        eventPayload.put("token_id", entitlement.tokenId().toString());
        eventPayload.put("actor_id", entitlement.actorId());
        eventPayload.put("patient_ref", entry.patientRef());
        eventPayload.put("action_type", entry.actionType());
        eventPayload.put("captured_at", entry.capturedAt());
        eventPayload.put("correlation_id", correlationId);
        eventPayload.put("status", "QUEUED");
        outbox.setPayloadJson(toJson(eventPayload));
        outbox.setPartitionKey(entry.patientRef());
        outboxRepository.save(outbox);

        return actionId;
    }

    private void emitSyncBatchEvent(OfflineEntitlement entitlement, UUID batchId,
                                     int accepted, int rejected, int total) {
        OutboxEventEntity outbox = new OutboxEventEntity();
        outbox.setAggregateType("SyncBatch");
        outbox.setAggregateId(batchId.toString());
        outbox.setEventType("impilo.offline.sync.completed.v1");
        outbox.setTenantId(entitlement.tenantId());
        outbox.setPodId(entitlement.facilityId() != null
                ? entitlement.facilityId().toString() : "national-spine");
        outbox.setSubjectId(batchId.toString());
        outbox.setSubjectType("SyncBatch");
        outbox.setOccurredAt(OffsetDateTime.now());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("batch_id", batchId.toString());
        payload.put("token_id", entitlement.tokenId().toString());
        payload.put("actor_id", entitlement.actorId());
        payload.put("facility_id", entitlement.facilityId() != null
                ? entitlement.facilityId().toString() : null);
        payload.put("total_actions", total);
        payload.put("accepted_count", accepted);
        payload.put("rejected_count", rejected);
        payload.put("status", rejected == 0 ? "SYNCED" : "PARTIAL");
        outbox.setPayloadJson(toJson(payload));
        outbox.setPartitionKey(batchId.toString());
        outboxRepository.save(outbox);
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON serialization failed", e);
        }
    }
}
