package zw.gov.mohcc.impilo.patientsafety.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.patientsafety.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.patientsafety.repository.OutboxEventRepository;
import zw.gov.mohcc.impilo.sharedkernel.events.EventTopicRegistry;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Writes pharmacovigilance domain events to the transactional outbox for Kafka publication via
 * {@link zw.gov.mohcc.impilo.patientsafety.events.PatientSafetyOutboxPublisher}.
 *
 * <p>Aggregate types follow canonical uppercase names ({@code SAFETY_REPORT}, {@code SAFETY_CASE},
 * {@code AEFI_INVESTIGATION}). {@code payloadJson} stores only the business payload; v1.1 envelopes
 * are assembled at publish time. Surveillance-service consumes the {@code report.submitted} /
 * {@code report.serious} stream for signal/cluster detection — it does not own the regulatory case.</p>
 */
@Service
public class SafetyEventService {

    private static final EventTopicRegistry TOPICS = new EventTopicRegistry("patientsafety");

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final OutboxEventRepository outboxRepository;

    public SafetyEventService(OutboxEventRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    @Transactional
    public void emitReportSubmitted(UUID reportId, UUID correlationId, UUID tenantId, String podId,
                                    String subjectId, Map<String, Object> payload) {
        emit("SAFETY_REPORT", reportId.toString(), TOPICS.eventType("report", "submitted"),
                correlationId, tenantId, podId, subjectId, "SAFETY_REPORT", payload);
    }

    /** Signal stream consumed by surveillance for serious / clustering events. */
    @Transactional
    public void emitReportSerious(UUID reportId, UUID correlationId, UUID tenantId, String podId,
                                  String subjectId, Map<String, Object> payload) {
        emit("SAFETY_REPORT", reportId.toString(), TOPICS.eventType("report", "serious"),
                correlationId, tenantId, podId, subjectId, "SAFETY_REPORT", payload);
    }

    @Transactional
    public void emitCaseOpened(UUID caseId, UUID correlationId, UUID tenantId, String podId,
                               String subjectId, Map<String, Object> payload) {
        emit("SAFETY_CASE", caseId.toString(), TOPICS.eventType("case", "opened"),
                correlationId, tenantId, podId, subjectId, "SAFETY_CASE", payload);
    }

    @Transactional
    public void emitCaseTriaged(UUID caseId, UUID correlationId, UUID tenantId, String podId,
                                String subjectId, Map<String, Object> payload) {
        emit("SAFETY_CASE", caseId.toString(), TOPICS.eventType("case", "triaged"),
                correlationId, tenantId, podId, subjectId, "SAFETY_CASE", payload);
    }

    @Transactional
    public void emitFollowUpRequested(UUID caseId, UUID correlationId, UUID tenantId, String podId,
                                      String subjectId, Map<String, Object> payload) {
        emit("SAFETY_CASE", caseId.toString(), TOPICS.eventType("case", "follow_up_requested"),
                correlationId, tenantId, podId, subjectId, "SAFETY_CASE", payload);
    }

    @Transactional
    public void emitFollowUpReceived(UUID caseId, UUID correlationId, UUID tenantId, String podId,
                                     String subjectId, Map<String, Object> payload) {
        emit("SAFETY_CASE", caseId.toString(), TOPICS.eventType("case", "follow_up_received"),
                correlationId, tenantId, podId, subjectId, "SAFETY_CASE", payload);
    }

    @Transactional
    public void emitVigiFlowRecorded(UUID caseId, UUID correlationId, UUID tenantId, String podId,
                                     String subjectId, Map<String, Object> payload) {
        emit("SAFETY_CASE", caseId.toString(), TOPICS.eventType("case", "vigiflow_recorded"),
                correlationId, tenantId, podId, subjectId, "SAFETY_CASE", payload);
    }

    @Transactional
    public void emitCaseExportReady(UUID caseId, UUID correlationId, UUID tenantId, String podId,
                                    String subjectId, Map<String, Object> payload) {
        emit("SAFETY_CASE", caseId.toString(), TOPICS.eventType("case", "export_ready"),
                correlationId, tenantId, podId, subjectId, "SAFETY_CASE", payload);
    }

    @Transactional
    public void emitInvestigationOpened(UUID investigationId, UUID correlationId, UUID tenantId, String podId,
                                        String caseSubjectId, Map<String, Object> payload) {
        emit("AEFI_INVESTIGATION", investigationId.toString(), TOPICS.eventType("investigation", "opened"),
                correlationId, tenantId, podId, caseSubjectId, "AEFI_INVESTIGATION", payload);
    }

    @Transactional
    public void emitInvestigationUpdated(UUID investigationId, UUID correlationId, UUID tenantId, String podId,
                                         String caseSubjectId, Map<String, Object> payload) {
        emit("AEFI_INVESTIGATION", investigationId.toString(), TOPICS.eventType("investigation", "updated"),
                correlationId, tenantId, podId, caseSubjectId, "AEFI_INVESTIGATION", payload);
    }

    private void emit(String aggregateType, String aggregateId, String eventType,
                      UUID correlationId, UUID tenantId, String podId,
                      String subjectId, String subjectType, Map<String, Object> payload) {
        Map<String, Object> body = new LinkedHashMap<>(payload != null ? payload : Map.of());
        body.putIfAbsent("meta", meta(correlationId));
        String payloadJson;
        try {
            payloadJson = MAPPER.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize outbox payload", e);
        }
        OutboxEventEntity row = OutboxEventEntity.create(
                aggregateType, aggregateId, eventType, correlationId,
                tenantId, podId, subjectId != null ? subjectId : aggregateId, subjectType, payloadJson);
        outboxRepository.save(row);
    }

    public static Map<String, Object> meta(UUID correlationId) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("partition_key", correlationId != null ? correlationId.toString() : UUID.randomUUID().toString());
        meta.put("decision", Map.of("evidence", "bounded-stale-class-b"));
        return meta;
    }
}
