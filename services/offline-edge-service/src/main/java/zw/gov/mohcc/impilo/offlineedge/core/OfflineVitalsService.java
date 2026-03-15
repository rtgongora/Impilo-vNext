package zw.gov.mohcc.impilo.offlineedge.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.offline.sdk.entitlement.OfflineEntitlement;
import zw.gov.mohcc.impilo.offlineedge.api.dto.CaptureVitalsRequest;
import zw.gov.mohcc.impilo.offlineedge.domain.CapturedActionEntity;
import zw.gov.mohcc.impilo.offlineedge.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.offlineedge.repository.CapturedActionRepository;
import zw.gov.mohcc.impilo.offlineedge.repository.OutboxEventRepository;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Service for capturing offline vitals, authorized by JWT entitlement.
 *
 * <p>Each captured vital is stored as a {@link CapturedActionEntity} with
 * {@code QUEUED} status, and an audit event is emitted to the outbox
 * with the original correlation ID preserved.</p>
 */
@Service
public class OfflineVitalsService {

    private static final Logger log = LoggerFactory.getLogger(OfflineVitalsService.class);
    private final CapturedActionRepository actionRepository;
    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OfflineVitalsService(CapturedActionRepository actionRepository,
                                 OutboxEventRepository outboxRepository,
                                 ObjectMapper objectMapper) {
        this.actionRepository = actionRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Capture a vital sign reading from an offline session.
     *
     * @param entitlement verified offline entitlement (from JWT)
     * @param request     the vitals capture request
     * @return response map with action ID and status
     */
    @Transactional
    public Map<String, Object> captureVital(OfflineEntitlement entitlement, CaptureVitalsRequest request) {
        UUID actionId = UUID.randomUUID();
        OffsetDateTime capturedAt = OffsetDateTime.parse(request.capturedAt());
        String payloadJson = toJson(request.vitals());
        String correlationId = request.correlationId() != null
                ? request.correlationId() : UUID.randomUUID().toString();

        // Enforce max offline encounters limit
        long capturedCount = actionRepository.countByEntitlementId(entitlement.tokenId());
        if (entitlement.maxOfflineEncounters() > 0 && capturedCount >= entitlement.maxOfflineEncounters()) {
            throw new MaxEncountersExceededException(
                    "Maximum offline encounters (" + entitlement.maxOfflineEncounters() + ") reached");
        }

        // Store the captured action
        CapturedActionEntity action = new CapturedActionEntity(
                actionId,
                entitlement.tokenId(),  // link to the entitlement
                entitlement.tenantId(),
                entitlement.actorId(),
                request.patientRef(),
                "CAPTURE_VITALS",
                payloadJson,
                capturedAt,
                request.deviceId(),
                request.sequenceNum() > 0 ? request.sequenceNum() : 1
        );
        action.setIdempotencyKey(request.idempotencyKey());
        action.setHashChainPrev(request.hashChainPrev());
        action.setBreakGlass(request.breakGlass());
        actionRepository.save(action);

        // Write audit event to outbox
        OutboxEventEntity outbox = new OutboxEventEntity();
        outbox.setAggregateType("OfflineVital");
        outbox.setAggregateId(actionId.toString());
        outbox.setEventType("impilo.offline.vital.captured.v1");
        outbox.setCorrelationId(parseUuid(correlationId));
        outbox.setCausationId(parseUuid(correlationId));
        outbox.setIdempotencyKey(request.idempotencyKey());
        outbox.setTenantId(entitlement.tenantId());
        outbox.setPodId(entitlement.facilityId().toString());
        outbox.setSubjectId(request.patientRef());
        outbox.setSubjectType("VitalSign");
        outbox.setOccurredAt(capturedAt);

        Map<String, Object> eventPayload = new LinkedHashMap<>();
        eventPayload.put("action_id", actionId.toString());
        eventPayload.put("token_id", entitlement.tokenId().toString());
        eventPayload.put("actor_id", entitlement.actorId());
        eventPayload.put("facility_id", entitlement.facilityId().toString());
        eventPayload.put("patient_ref", request.patientRef());
        eventPayload.put("action_type", "CAPTURE_VITALS");
        eventPayload.put("captured_at", request.capturedAt());
        eventPayload.put("device_id", request.deviceId());
        eventPayload.put("sequence_num", action.getSequenceNum());
        eventPayload.put("hash_chain_prev", request.hashChainPrev());
        eventPayload.put("hash_chain_current", action.computeHash());
        eventPayload.put("break_glass", request.breakGlass());
        eventPayload.put("status", "QUEUED");
        outbox.setPayloadJson(toJson(eventPayload));
        outbox.setPartitionKey(request.patientRef());
        outboxRepository.save(outbox);

        // If break-glass, emit additional high-priority audit event
        if (request.breakGlass()) {
            OutboxEventEntity bgOutbox = new OutboxEventEntity();
            bgOutbox.setAggregateType("BreakGlass");
            bgOutbox.setAggregateId(actionId.toString());
            bgOutbox.setEventType("impilo.offline.break_glass.activated.v1");
            bgOutbox.setCorrelationId(parseUuid(correlationId));
            bgOutbox.setCausationId(parseUuid(correlationId));
            bgOutbox.setTenantId(entitlement.tenantId());
            bgOutbox.setPodId(entitlement.facilityId().toString());
            bgOutbox.setSubjectId(request.patientRef());
            bgOutbox.setSubjectType("BreakGlass");
            bgOutbox.setOccurredAt(capturedAt);

            Map<String, Object> bgPayload = new LinkedHashMap<>();
            bgPayload.put("action_id", actionId.toString());
            bgPayload.put("actor_id", entitlement.actorId());
            bgPayload.put("facility_id", entitlement.facilityId().toString());
            bgPayload.put("patient_ref", request.patientRef());
            bgPayload.put("reason", request.breakGlassReason());
            bgPayload.put("override_type", "BREAK_GLASS_CAPTURE");
            bgPayload.put("activated_at", capturedAt.toString());
            bgPayload.put("priority", "HIGH");
            bgOutbox.setPayloadJson(toJson(bgPayload));
            bgOutbox.setPartitionKey(entitlement.actorId());
            outboxRepository.save(bgOutbox);

            log.warn("BREAK-GLASS vitals capture: actionId={}, actor={}, patient={}, reason={}",
                    actionId, entitlement.actorId(), request.patientRef(), request.breakGlassReason());
        }

        log.info("Captured offline vital [actionId={}, patient={}, actor={}, facility={}]",
                actionId, request.patientRef(), entitlement.actorId(), entitlement.facilityId());

        // Build response
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("action_id", actionId.toString());
        response.put("entitlement_token_id", entitlement.tokenId().toString());
        response.put("patient_ref", request.patientRef());
        response.put("action_type", "CAPTURE_VITALS");
        response.put("status", "QUEUED");
        response.put("captured_at", request.capturedAt());
        response.put("sequence_num", action.getSequenceNum());
        response.put("hash_chain_current", action.computeHash());
        if (request.breakGlass()) {
            response.put("break_glass", true);
        }
        return response;
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

    public static class MaxEncountersExceededException extends RuntimeException {
        public MaxEncountersExceededException(String msg) { super(msg); }
    }
}
