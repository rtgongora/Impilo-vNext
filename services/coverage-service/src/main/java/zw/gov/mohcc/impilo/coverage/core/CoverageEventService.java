package zw.gov.mohcc.impilo.coverage.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.coverage.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.coverage.repository.OutboxEventRepository;
import zw.gov.mohcc.impilo.sharedkernel.events.EventTopicRegistry;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Writes domain events to the transactional outbox for Kafka publication via
 * {@link zw.gov.mohcc.impilo.coverage.events.CoverageOutboxPublisher}.
 *
 * <p>Aggregate types follow canonical uppercase names (e.g. {@code COVERAGE_PLAN}).
 * {@code payloadJson} stores only the business payload object — v1.1 envelopes are built at publish time.</p>
 */
@Service
public class CoverageEventService {

    private static final EventTopicRegistry TOPICS = new EventTopicRegistry("coverage");

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final OutboxEventRepository outboxRepository;

    public CoverageEventService(OutboxEventRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    @Transactional
    public void emitPlanCreated(UUID planId, UUID correlationId, UUID tenantId, String podId,
                                Map<String, Object> payload) {
        emit("COVERAGE_PLAN", planId.toString(), TOPICS.eventType("plan", "created"),
                correlationId, tenantId, podId, planId.toString(), "COVERAGE_PLAN", payload);
    }

    @Transactional
    public void emitEligibilityChecked(UUID eligibilityCheckId, UUID correlationId, UUID tenantId, String podId,
                                       UUID coverageId, Map<String, Object> payload) {
        emit("ELIGIBILITY", eligibilityCheckId.toString(), TOPICS.eventType("eligibility", "checked"),
                correlationId, tenantId, podId, coverageId.toString(), "MEMBER_COVERAGE", payload);
    }

    @Transactional
    public void emitMemberEnrolled(UUID memberCoverageId, UUID correlationId, UUID tenantId, String podId,
                                   UUID planId, Map<String, Object> payload) {
        emit("MEMBER_COVERAGE", memberCoverageId.toString(), TOPICS.eventType("member", "enrolled"),
                correlationId, tenantId, podId, planId.toString(), "MEMBER_COVERAGE", payload);
    }

    @Transactional
    public void emitPreauthRequested(UUID preauthId, UUID correlationId, UUID tenantId, String podId,
                                     UUID coverageId, Map<String, Object> payload) {
        emit("PREAUTH", preauthId.toString(), TOPICS.eventType("preauth", "requested"),
                correlationId, tenantId, podId, coverageId.toString(), "MEMBER_COVERAGE", payload);
    }

    @Transactional
    public void emitPreauthApproved(UUID preauthId, UUID correlationId, UUID tenantId, String podId,
                                    UUID coverageId, Map<String, Object> payload) {
        emit("PREAUTH", preauthId.toString(), TOPICS.eventType("preauth", "approved"),
                correlationId, tenantId, podId, coverageId.toString(), "MEMBER_COVERAGE", payload);
    }

    @Transactional
    public void emitPreauthDenied(UUID preauthId, UUID correlationId, UUID tenantId, String podId,
                                  UUID coverageId, Map<String, Object> payload) {
        emit("PREAUTH", preauthId.toString(), TOPICS.eventType("preauth", "denied"),
                correlationId, tenantId, podId, coverageId.toString(), "MEMBER_COVERAGE", payload);
    }

    @Transactional
    public void emitClaimSubmitted(UUID claimId, UUID correlationId, UUID tenantId, String podId,
                                  UUID coverageId, Map<String, Object> payload) {
        emit("CLAIM", claimId.toString(), TOPICS.eventType("claim", "submitted"),
                correlationId, tenantId, podId, coverageId.toString(), "MEMBER_COVERAGE", payload);
    }

    @Transactional
    public void emitRemittanceCreated(UUID remittanceId, UUID correlationId, UUID tenantId, String podId,
                                      String subjectId, Map<String, Object> payload) {
        emit("REMITTANCE", remittanceId.toString(), TOPICS.eventType("remittance", "created"),
                correlationId, tenantId, podId, subjectId, "REMITTANCE", payload);
    }

    @Transactional
    public void emitAppealSubmitted(UUID appealId, UUID correlationId, UUID tenantId, String podId,
                                    String claimKey, Map<String, Object> payload) {
        emit("APPEAL", appealId.toString(), TOPICS.eventType("appeal", "submitted"),
                correlationId, tenantId, podId, claimKey, "APPEAL", payload);
    }

    @Transactional
    public void emitAppealUnderReview(UUID appealId, UUID correlationId, UUID tenantId, String podId,
                                      String claimKey, Map<String, Object> payload) {
        emit("APPEAL", appealId.toString(), TOPICS.eventType("appeal", "under_review"),
                correlationId, tenantId, podId, claimKey, "APPEAL", payload);
    }

    /**
     * Appeal final disposition for downstream rails (explicit legacy-style {@code coverage.appeal.decided} type).
     */
    @Transactional
    public void emitAppealDecided(UUID appealId, UUID correlationId, UUID tenantId, String podId,
                                  String claimId, String decision, String decisionReason, String decidedBy,
                                  OffsetDateTime decidedAt, Map<String, Object> payload) {
        LinkedHashMap<String, Object> body = new LinkedHashMap<>();
        if (payload != null) {
            body.putAll(payload);
        }
        body.put("appeal_id", appealId.toString());
        body.put("claim_id", claimId);
        body.put("decision", decision);
        body.put("decision_reason", decisionReason);
        body.put("decided_by", decidedBy);
        body.put("decided_at", decidedAt != null ? decidedAt.toString() : null);
        emit("APPEAL", appealId.toString(), "coverage.appeal.decided",
                correlationId, tenantId, podId, appealId.toString(), "APPEAL", body);
    }

    @Transactional
    public void emitClaimResubmitted(UUID correlationId, UUID tenantId, String podId,
                                      String mushexClaimId, Map<String, Object> payload) {
        LinkedHashMap<String, Object> body = new LinkedHashMap<>();
        if (payload != null) {
            body.putAll(payload);
        }
        body.putIfAbsent("claim_id", mushexClaimId);
        emit("CLAIM", mushexClaimId, "coverage.claim.resubmitted",
                correlationId, tenantId, podId, mushexClaimId, "CLAIM", body);
    }

    /**
     * Legacy-style create hook used by provider contracting controllers.
     */
    @Transactional
    public void emitCreated(String entityName, String entityId, UUID correlationId, UUID tenantId, String podId,
                            Map<String, Object> state) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>(state != null ? state : Map.of());
        if (!payload.containsKey("meta")) {
            payload.put("meta", meta(correlationId));
        }
        switch (entityName) {
            case "preauth" -> {
                UUID preauthId = UUID.fromString(entityId);
                UUID coverageId = UUID.fromString(state.get("coverage_id").toString());
                emitPreauthRequested(preauthId, correlationId, tenantId, podId, coverageId, payload);
            }
            case "contract" -> emit("PROVIDER_CONTRACT", entityId, TOPICS.eventType("provider_contract", "created"),
                    correlationId, tenantId, podId, entityId, "PROVIDER_CONTRACT", payload);
            case "network" -> emit("PROVIDER_NETWORK", entityId, TOPICS.eventType("provider_network", "created"),
                    correlationId, tenantId, podId, entityId, "PROVIDER_NETWORK", payload);
            case "network_member" -> emit("NETWORK_MEMBER", entityId, TOPICS.eventType("network_member", "created"),
                    correlationId, tenantId, podId,
                    String.valueOf(state.getOrDefault("network_id", entityId)), "NETWORK_MEMBER", payload);
            default -> throw new IllegalArgumentException("Unsupported emitCreated entity: " + entityName);
        }
    }

    /**
     * Legacy-style status hook used by provider contracting controllers.
     */
    @Transactional
    public void emitStatusChange(String entityName, String entityId, String action, UUID correlationId,
                                 UUID tenantId, String podId, Map<String, Object> changed) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>(changed != null ? changed : Map.of());
        if (!payload.containsKey("meta")) {
            payload.put("meta", meta(correlationId));
        }
        String act = action == null ? "updated" : action.toLowerCase();
        switch (entityName) {
            case "contract" -> emit("PROVIDER_CONTRACT", entityId,
                    TOPICS.eventType("provider_contract", act.replace(' ', '_')),
                    correlationId, tenantId, podId, entityId, "PROVIDER_CONTRACT", payload);
            case "network_member" -> emit("NETWORK_MEMBER", entityId,
                    TOPICS.eventType("network_member", act.replace(' ', '_')),
                    correlationId, tenantId, podId,
                    changed != null && changed.get("network_id") != null
                            ? changed.get("network_id").toString()
                            : entityId,
                    "NETWORK_MEMBER", payload);
            default -> throw new IllegalArgumentException("Unsupported emitStatusChange entity: " + entityName);
        }
    }

    private void emit(String aggregateType, String aggregateId, String eventType,
                      UUID correlationId, UUID tenantId, String podId,
                      String subjectId, String subjectType, Map<String, Object> payload) {
        String payloadJson;
        try {
            payloadJson = MAPPER.writeValueAsString(payload != null ? payload : Map.of());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize outbox payload", e);
        }

        OutboxEventEntity row = OutboxEventEntity.create(
                aggregateType, aggregateId, eventType, correlationId,
                tenantId, podId, subjectId, subjectType, payloadJson);
        outboxRepository.save(row);
    }

    public static Map<String, Object> meta(UUID correlationId) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("partition_key", correlationId != null ? correlationId.toString() : UUID.randomUUID().toString());
        meta.put("decision", Map.of("evidence", "bounded-stale-class-b"));
        return meta;
    }
}
