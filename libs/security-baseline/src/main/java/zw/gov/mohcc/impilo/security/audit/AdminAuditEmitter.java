package zw.gov.mohcc.impilo.security.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import zw.gov.mohcc.impilo.sharedkernel.events.EventEnvelope;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Emits immutable admin audit events for sensitive endpoints.
 * <p>
 * Events are written to the outbox via a pluggable {@link AdminAuditSink},
 * wrapped in the standard v1.1 {@link EventEnvelope}. This guarantees
 * at-least-once delivery to the audit ledger via the outbox pattern.
 * <p>
 * Thread-safe. Stateless apart from the injected sink and service name.
 */
public final class AdminAuditEmitter {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final String serviceName;
    private final AdminAuditSink sink;

    /**
     * @param serviceName  the producing service (e.g. "tshepo", "vito")
     * @param sink         where to persist the audit event (outbox table, in-memory for tests)
     */
    public AdminAuditEmitter(String serviceName, AdminAuditSink sink) {
        this.serviceName = Objects.requireNonNull(serviceName, "serviceName is required");
        this.sink = Objects.requireNonNull(sink, "sink is required");
    }

    /**
     * Emit an admin audit event.
     *
     * @param action        the admin action (e.g. "POLICY_UPDATE", "USER_ROLE_CHANGE")
     * @param actorId       who performed the action
     * @param actorType     type of actor (USER, SERVICE, SYSTEM)
     * @param tenantId      tenant scope
     * @param podId         originating facility/pod
     * @param correlationId request correlation ID
     * @param subjectType   what was acted upon (e.g. "Policy", "User")
     * @param subjectId     identifier of the subject
     * @param details       action-specific details (must not contain secrets)
     */
    public void emit(String action, String actorId, String actorType,
                     String tenantId, String podId, String correlationId,
                     String subjectType, String subjectId,
                     Map<String, Object> details) {

        Objects.requireNonNull(action, "action is required");
        Objects.requireNonNull(actorId, "actorId is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(correlationId, "correlationId is required");

        OffsetDateTime now = OffsetDateTime.now();

        EventEnvelope envelope = EventEnvelope.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("impilo." + serviceName + ".admin_audit." + action.toLowerCase() + ".v1")
                .schemaVersion(1)
                .correlationId(correlationId)
                .causationId(correlationId)
                .idempotencyKey(UUID.randomUUID().toString())
                .producer(serviceName)
                .tenantId(tenantId)
                .podId(podId != null ? podId : "unknown")
                .occurredAt(now)
                .emittedAt(now)
                .subjectType(subjectType != null ? subjectType : "AdminAction")
                .subjectId(subjectId != null ? subjectId : action)
                .payload(Map.of(
                        "action", action,
                        "actor_id", actorId,
                        "actor_type", actorType != null ? actorType : "USER",
                        "details", details != null ? details : Map.of(),
                        "immutable", true
                ))
                .meta(Map.of("audit_class", "ADMIN_ACTION"))
                .build();

        String payloadJson;
        try {
            payloadJson = MAPPER.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize admin audit event", e);
        }

        AdminAuditRecord record = new AdminAuditRecord(
                envelope.eventId(),
                "AdminAudit",
                subjectId != null ? subjectId : action,
                envelope.eventType(),
                payloadJson,
                now.toInstant()
        );

        sink.persist(record);
    }

    /**
     * Pluggable persistence for admin audit events. Implementations write
     * to the service's event_outbox table or an in-memory list (for tests).
     */
    public interface AdminAuditSink {
        void persist(AdminAuditRecord record);
    }
}
