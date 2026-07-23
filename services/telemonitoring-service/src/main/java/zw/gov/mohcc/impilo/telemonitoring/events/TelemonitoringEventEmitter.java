package zw.gov.mohcc.impilo.telemonitoring.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.telemonitoring.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.telemonitoring.persistence.repository.EventOutboxRepository;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Writes domain events into the telemonitoring transactional outbox. Called inside the
 * same transaction as the state change so events are never lost (relayed by
 * {@link TelemonitoringOutboxPublisher}).
 *
 * <p>Plan lifecycle event types follow the epic contract:
 * {@code telemonitoring.plan.{created,activated,amended,suspended,completed,cancelled}.v1}.</p>
 */
@Component
public class TelemonitoringEventEmitter {

    /** Aggregate type used for all monitoring-plan events. */
    public static final String AGGREGATE_MONITORING_PLAN = "MONITORING_PLAN";

    /** Aggregate type used for all device-assignment events (OF-B24). */
    public static final String AGGREGATE_DEVICE_ASSIGNMENT = "DEVICE_ASSIGNMENT";

    /** Aggregate type used for programme-catalogue governance events (OF-B21). */
    public static final String AGGREGATE_MONITORING_PROGRAMME = "MONITORING_PROGRAMME";

    /** Aggregate type used for telemetry-reading / observation-writer events (OF-B25). */
    public static final String AGGREGATE_TELEMETRY_READING = "TELEMETRY_READING";

    private static final Logger log = LoggerFactory.getLogger(TelemonitoringEventEmitter.class);

    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public TelemonitoringEventEmitter(EventOutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /** Build a plan lifecycle event type, e.g. {@code planEventType("created")}. */
    public static String planEventType(String action) {
        return "telemonitoring.plan." + action + ".v1";
    }

    /** Build a programme governance event type, e.g. {@code programmeEventType("retired")}. */
    public static String programmeEventType(String action) {
        return "telemonitoring.programme." + action + ".v1";
    }

    public void emitPlanEvent(String action, UUID planId, String patientCpid,
                              Map<String, Object> payload, UUID tenantId) {
        emit(AGGREGATE_MONITORING_PLAN, planId.toString(), planEventType(action),
                "PATIENT", patientCpid, payload, tenantId);
    }

    /** Build a device-assignment event type, e.g. {@code deviceEventType("assigned")} (OF-B24). */
    public static String deviceEventType(String action) {
        return "telemonitoring.device." + action + ".v1";
    }

    public void emitDeviceEvent(String action, UUID assignmentId, String patientCpid,
                                Map<String, Object> payload, UUID tenantId) {
        emit(AGGREGATE_DEVICE_ASSIGNMENT, assignmentId.toString(), deviceEventType(action),
                "PATIENT", patientCpid, payload, tenantId);
    }

    /** Build a reading pipeline event type, e.g. {@code readingEventType("quarantined")} (OF-B25). */
    public static String readingEventType(String action) {
        return "telemonitoring.reading." + action + ".v1";
    }

    /** Build an observation-writer event type, e.g. {@code observationEventType("recorded")} (OF-B25). */
    public static String observationEventType(String action) {
        return "telemonitoring.observation." + action + ".v1";
    }

    /**
     * Reading pipeline event ({@code telemonitoring.reading.{action}.v1}). Subject is the
     * DEVICE, never a patient — quarantined readings carry no patient attribution (#62).
     */
    public void emitReadingEvent(String action, UUID readingId, String deviceRef,
                                 Map<String, Object> payload, UUID tenantId) {
        emit(AGGREGATE_TELEMETRY_READING, readingId.toString(), readingEventType(action),
                "DEVICE", deviceRef, payload, tenantId);
    }

    /** Observation-writer event ({@code telemonitoring.observation.{action}.v1}), patient-subject. */
    public void emitObservationEvent(String action, UUID readingId, String patientCpid,
                                     Map<String, Object> payload, UUID tenantId) {
        emit(AGGREGATE_TELEMETRY_READING, readingId.toString(), observationEventType(action),
                "PATIENT", patientCpid, payload, tenantId);
    }

    /** Programme-catalogue governance event ({@code telemonitoring.programme.{action}.v1}). */
    public void emitProgrammeEvent(String action, String programmeCode,
                                   Map<String, Object> payload, UUID tenantId) {
        emit(AGGREGATE_MONITORING_PROGRAMME, programmeCode, programmeEventType(action),
                "PROGRAMME", programmeCode, payload, tenantId);
    }

    public void emit(String aggregateType, String aggregateId, String eventType,
                     String subjectType, String subjectId, Map<String, Object> payload, UUID tenantId) {
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setSubjectType(subjectType);
        event.setSubjectId(subjectId);
        event.setTenantId(tenantId);
        event.setPartitionKey(aggregateId);
        event.setIdempotencyKey(aggregateType + ":" + aggregateId + ":" + eventType + ":" + UUID.randomUUID());
        event.setOccurredAt(OffsetDateTime.now());
        try {
            TrustContext ctx = TrustContextHolder.get();
            if (ctx != null && ctx.correlationId() != null) {
                event.setCorrelationId(ctx.correlationId());
            }
        } catch (IllegalStateException ignored) {
            // no trust context in tests / async paths
        }
        try {
            event.setPayloadJson(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize outbox payload for {}: {}", eventType, e.getMessage());
            event.setPayloadJson("{}");
        }
        outboxRepository.save(event);
    }
}
