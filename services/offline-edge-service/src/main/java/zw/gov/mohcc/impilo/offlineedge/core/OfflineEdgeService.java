package zw.gov.mohcc.impilo.offlineedge.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.offlineedge.api.dto.*;
import zw.gov.mohcc.impilo.offlineedge.client.ButanoFhirClient;
import zw.gov.mohcc.impilo.offlineedge.domain.*;
import zw.gov.mohcc.impilo.offlineedge.repository.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class OfflineEdgeService {

    private static final Logger log = LoggerFactory.getLogger(OfflineEdgeService.class);

    private final EntitlementRepository entitlementRepository;
    private final CapturedActionRepository actionRepository;
    private final ReconciliationBatchRepository batchRepository;
    private final OutboxEventRepository outboxRepository;
    private final ConflictReviewRepository conflictRepository;
    private final ButanoFhirClient butanoFhirClient;
    private final ObjectMapper objectMapper;
    private final String hmacSecret;
    private final int entitlementTtlHours;

    public OfflineEdgeService(EntitlementRepository entitlementRepository,
                               CapturedActionRepository actionRepository,
                               ReconciliationBatchRepository batchRepository,
                               OutboxEventRepository outboxRepository,
                               ConflictReviewRepository conflictRepository,
                               ButanoFhirClient butanoFhirClient,
                               ObjectMapper objectMapper,
                               @Value("${impilo.offline.hmac-secret}") String hmacSecret,
                               @Value("${impilo.offline.entitlement-ttl-hours}") int entitlementTtlHours) {
        this.entitlementRepository = entitlementRepository;
        this.actionRepository = actionRepository;
        this.batchRepository = batchRepository;
        this.outboxRepository = outboxRepository;
        this.conflictRepository = conflictRepository;
        this.butanoFhirClient = butanoFhirClient;
        this.objectMapper = objectMapper;
        this.hmacSecret = hmacSecret;
        this.entitlementTtlHours = entitlementTtlHours;
    }

    @Transactional
    public EntitlementEntity issueEntitlement(UUID tenantId, String podId, String correlationId,
                                               String idempotencyKey, IssueEntitlementRequest request) {
        UUID entitlementId = UUID.randomUUID();
        String workflowType = request.workflowType() != null ? request.workflowType() : "CAPTURE_VITALS";
        String scopeJson = toJson(request.scope() != null ? request.scope() : Map.of("workflow", workflowType));

        // Generate signed token hash (HMAC-SHA256)
        String tokenInput = entitlementId + "|" + tenantId + "|" + request.actorId() + "|" + workflowType;
        String tokenHash = computeHmac(tokenInput);

        OffsetDateTime expiresAt = OffsetDateTime.now().plusHours(entitlementTtlHours);
        EntitlementEntity entitlement = new EntitlementEntity(entitlementId, tenantId,
                request.actorId(), request.facilityRef(), workflowType, scopeJson, tokenHash, expiresAt);
        entitlementRepository.save(entitlement);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("entitlement_id", entitlementId.toString());
        payload.put("actor_id", request.actorId());
        payload.put("facility_ref", request.facilityRef());
        payload.put("workflow_type", workflowType);
        payload.put("expires_at", expiresAt.toString());

        appendOutboxEvent("impilo.offline.entitlement.issued.v1", entitlementId.toString(),
                tenantId, podId, correlationId, idempotencyKey, payload, request.actorId());

        log.info("Issued entitlement [id={}, actor={}, workflow={}]", entitlementId, request.actorId(), workflowType);
        return entitlement;
    }

    @Transactional
    public CapturedActionEntity captureAction(UUID tenantId, String podId, String correlationId,
                                                String idempotencyKey, CaptureActionRequest request) {
        // Verify entitlement exists and is valid
        EntitlementEntity entitlement = entitlementRepository.findById(request.entitlementId())
                .orElseThrow(() -> new InvalidEntitlementException("Entitlement not found: " + request.entitlementId()));

        if (entitlement.isRevoked()) throw new InvalidEntitlementException("Entitlement is revoked");
        if (entitlement.getExpiresAt().isBefore(OffsetDateTime.now()))
            throw new InvalidEntitlementException("Entitlement has expired");

        UUID actionId = UUID.randomUUID();
        OffsetDateTime capturedAt = OffsetDateTime.parse(request.capturedAt());
        String payloadJson = toJson(request.payload());

        CapturedActionEntity action = new CapturedActionEntity(actionId, request.entitlementId(),
                tenantId, entitlement.getActorId(), request.patientRef(), request.actionType(),
                payloadJson, capturedAt, request.deviceId(),
                request.sequenceNum() > 0 ? request.sequenceNum() : 1);
        actionRepository.save(action);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action_id", actionId.toString());
        payload.put("entitlement_id", request.entitlementId().toString());
        payload.put("patient_ref", request.patientRef());
        payload.put("action_type", request.actionType());
        payload.put("status", "QUEUED");

        appendOutboxEvent("impilo.offline.action.recorded.v1", actionId.toString(),
                tenantId, podId, correlationId, idempotencyKey, payload, request.patientRef());

        log.info("Captured offline action [id={}, type={}, patient={}]", actionId, request.actionType(), request.patientRef());
        return action;
    }

    @Transactional
    public ReconciliationBatchEntity replayActions(UUID entitlementId, UUID tenantId, String podId, String correlationId) {
        List<CapturedActionEntity> queued = actionRepository
                .findByEntitlementIdAndStatusOrderBySequenceNumAsc(entitlementId, "QUEUED");

        if (queued.isEmpty()) throw new IllegalStateException("No queued actions for entitlement: " + entitlementId);

        UUID batchId = UUID.randomUUID();
        ReconciliationBatchEntity batch = new ReconciliationBatchEntity(batchId, tenantId, entitlementId, queued.size());
        batch.setStatus("IN_PROGRESS");
        batch.setStartedAt(OffsetDateTime.now());
        batchRepository.save(batch);

        int replayed = 0, failed = 0, conflicts = 0;
        for (CapturedActionEntity action : queued) {
            try {
                // Replay to BUTANO (FHIR Observation write) for vitals actions
                if ("CAPTURE_VITALS".equals(action.getActionType()) || "VITAL_SIGN".equals(action.getActionType())) {
                    Map<String, Object> vitalsPayload = parsePayload(action.getPayloadJson());
                    ButanoFhirClient.FhirReplayResult fhirResult = butanoFhirClient.postObservation(
                            action.getPatientRef(), vitalsPayload, tenantId.toString(), correlationId);

                    if (fhirResult.isConflict()) {
                        // Queue for manual review
                        action.setStatus("CONFLICT");
                        action.setReplayedAt(OffsetDateTime.now());
                        action.setReplayError(fhirResult.detail());
                        actionRepository.save(action);

                        ConflictReviewEntity conflict = new ConflictReviewEntity();
                        conflict.setActionId(action.getActionId());
                        conflict.setBatchId(batchId);
                        conflict.setTenantId(tenantId);
                        conflict.setPatientRef(action.getPatientRef());
                        conflict.setActionType(action.getActionType());
                        conflict.setConflictReason(fhirResult.detail());
                        conflict.setOfflinePayload(action.getPayloadJson());
                        conflictRepository.save(conflict);

                        // Emit conflict audit event
                        Map<String, Object> conflictPayload = new LinkedHashMap<>();
                        conflictPayload.put("action_id", action.getActionId().toString());
                        conflictPayload.put("conflict_id", conflict.getConflictId().toString());
                        conflictPayload.put("patient_ref", action.getPatientRef());
                        conflictPayload.put("reason", fhirResult.detail());
                        conflictPayload.put("batch_id", batchId.toString());
                        appendOutboxEvent("impilo.offline.action.conflict.v1", action.getActionId().toString(),
                                tenantId, podId, correlationId, null, conflictPayload, action.getPatientRef());

                        conflicts++;
                        continue;
                    } else if (!fhirResult.isSuccess()) {
                        throw new RuntimeException("BUTANO replay failed: " + fhirResult.detail());
                    }
                }

                action.setStatus("REPLAYED");
                action.setReplayedAt(OffsetDateTime.now());
                actionRepository.save(action);

                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("action_id", action.getActionId().toString());
                payload.put("entitlement_id", entitlementId.toString());
                payload.put("action_type", action.getActionType());
                payload.put("patient_ref", action.getPatientRef());
                payload.put("batch_id", batchId.toString());

                appendOutboxEvent("impilo.offline.action.replayed.v1", action.getActionId().toString(),
                        tenantId, podId, correlationId, null, payload, action.getPatientRef());
                replayed++;
            } catch (Exception e) {
                action.setStatus("FAILED");
                action.setReplayError(e.getMessage());
                actionRepository.save(action);
                failed++;
            }
        }

        batch.setReplayedCount(replayed);
        batch.setFailedCount(failed + conflicts);
        batch.setStatus(failed == 0 && conflicts == 0 ? "COMPLETED"
                : conflicts > 0 && failed == 0 ? "CONFLICTS_PENDING" : "PARTIAL");
        batch.setCompletedAt(OffsetDateTime.now());
        batchRepository.save(batch);

        log.info("Replay batch [batchId={}, replayed={}, conflicts={}, failed={}]", batchId, replayed, conflicts, failed);
        return batch;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parsePayload(String payloadJson) {
        try {
            return objectMapper.readValue(payloadJson, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    @Transactional(readOnly = true)
    public Optional<EntitlementEntity> getEntitlement(UUID entitlementId) {
        return entitlementRepository.findById(entitlementId);
    }

    @Transactional(readOnly = true)
    public boolean verifyEntitlementToken(UUID entitlementId, String providedTokenHash) {
        return entitlementRepository.findById(entitlementId)
                .map(e -> !e.isRevoked() && !e.getExpiresAt().isBefore(OffsetDateTime.now())
                        && e.getTokenHash().equals(providedTokenHash))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public Page<CapturedActionEntity> listActions(UUID tenantId, String status, int page, int size) {
        return actionRepository.findFiltered(tenantId, status, PageRequest.of(page, size));
    }

    private String computeHmac(String input) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) { throw new RuntimeException("HMAC computation failed", e); }
    }

    private void appendOutboxEvent(String eventType, String aggregateId, UUID tenantId,
                                    String podId, String correlationId, String idempotencyKey,
                                    Map<String, Object> payload, String partitionKey) {
        OutboxEventEntity outbox = new OutboxEventEntity();
        outbox.setAggregateType("OfflineAction");
        outbox.setAggregateId(aggregateId);
        outbox.setEventType(eventType);
        outbox.setCorrelationId(correlationId != null ? UUID.fromString(correlationId) : null);
        outbox.setCausationId(correlationId != null ? UUID.fromString(correlationId) : null);
        outbox.setIdempotencyKey(idempotencyKey);
        outbox.setTenantId(tenantId);
        outbox.setPodId(podId != null ? podId : "national-spine");
        outbox.setSubjectId(aggregateId);
        outbox.setSubjectType("OfflineAction");
        outbox.setOccurredAt(OffsetDateTime.now());
        outbox.setPayloadJson(toJson(payload));
        outbox.setPartitionKey(partitionKey);
        outboxRepository.save(outbox);
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try { return objectMapper.writeValueAsString(obj); }
        catch (JsonProcessingException e) { throw new RuntimeException("JSON serialization failed", e); }
    }

    public static class InvalidEntitlementException extends RuntimeException {
        public InvalidEntitlementException(String msg) { super(msg); }
    }
}
