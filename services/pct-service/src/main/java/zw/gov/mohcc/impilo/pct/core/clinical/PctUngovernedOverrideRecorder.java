package zw.gov.mohcc.impilo.pct.core.clinical;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.sharedkernel.security.UngovernedOverride;
import zw.gov.mohcc.impilo.sharedkernel.security.UngovernedOverrideRecorder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Records an ungoverned break-glass override into pct's transactional outbox, shaped so the audit
 * plane can ingest it.
 *
 * <p>Same reasoning as madi's recorder: the write goes to pct's own database, in the same
 * transaction as the clinical write it describes, rather than over HTTP to the audit service. This
 * code runs precisely when a remote call has just failed, so a record-of-last-resort that depends
 * on another remote call would be most likely to vanish exactly when it matters.</p>
 *
 * <p><b>Guaranteed:</b> the local outbox row, which is transactional with the clinical write and is
 * marked published rather than deleted, so it remains queryable indefinitely.
 * <b>Best-effort:</b> arrival in tshepo-audit's hash chain, which depends on the poller draining
 * and that consumer running.</p>
 *
 * <p>The event type is distinct from every clinical event pct emits, so an override is findable
 * rather than buried among ordinary care traffic.</p>
 */
@Component
public class PctUngovernedOverrideRecorder implements UngovernedOverrideRecorder {

    /** Distinct from ALLOW so the audit plane can select overrides without service-specific knowledge. */
    static final String OUTCOME = "ALLOW_UNGOVERNED";

    /** Routed to {@code tshepo.audit.events} by {@code OutboxPublisher.routeTopic}. */
    public static final String AGGREGATE_TYPE = "BREAK_GLASS_OVERRIDE";

    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public PctUngovernedOverrideRecorder(EventOutboxRepository outboxRepository,
                                         ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Deliberately does not catch, including the serialisation failure. A payload that will not
     * serialise must not be downgraded to an empty one and saved: a row saying an override happened
     * but not who, what or why is not a weaker record, it is a misleading one.</p>
     */
    @Override
    public void record(UngovernedOverride override) {
        TrustContext ctx = trustContext();

        // Shaped as tshepo-audit's AuditEventRequest so AuditKafkaConsumer can append it to the
        // hash chain directly; that record ignores unknown properties, so detail is carried.
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", override.tenantId());
        payload.put("eventType", UngovernedOverride.EVENT_TYPE);
        payload.put("actorId", override.actorId());
        payload.put("actorType", actorType(ctx));
        payload.put("subjectRef", override.subjectRef());
        payload.put("resourceType", AGGREGATE_TYPE);
        payload.put("resourceId", override.action());
        payload.put("action", override.action());
        payload.put("outcome", OUTCOME);
        payload.put("purposeOfUse", override.purposeOfUse());
        payload.put("correlationId", correlationId(ctx));

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("governed", false);
        // Labelled unverified because it is: the client-supplied string that selected the emergency
        // path, recorded so a reviewer can see what was claimed, not offered as evidence.
        detail.put("purposeOfUseUnverified", override.purposeOfUse());
        detail.put("grantTokenUnvalidated", override.grantToken());
        detail.put("grantCheckFailureReason", override.reason());
        detail.put("reviewObligation", override.reviewObligation());
        detail.put("occurredAt", override.occurredAt().toString());
        payload.put("detail", detail);

        EventOutboxEntity outbox = new EventOutboxEntity();
        outbox.setAggregateType(AGGREGATE_TYPE);
        outbox.setAggregateId(override.actorId() + ":" + override.action());
        outbox.setEventType(UngovernedOverride.EVENT_TYPE);
        outbox.setPayload(toJson(payload));
        outbox.setTenantId(UUID.fromString(override.tenantId()));
        outboxRepository.save(outbox);
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Could not serialise the ungoverned-override audit record; refusing rather than "
                            + "writing a row that says an override happened but not what it was", e);
        }
    }

    private static TrustContext trustContext() {
        try {
            return TrustContextHolder.get();
        } catch (RuntimeException e) {
            return null; // no request context (background thread, test harness)
        }
    }

    /**
     * The caller's actor type from the trust context. Falls back to {@code SYSTEM} rather than
     * guessing {@code PROVIDER}: an invented actor type reads as evidence about who acted.
     */
    private static String actorType(TrustContext ctx) {
        if (ctx != null && ctx.actorType() != null && !ctx.actorType().isBlank()) {
            return ctx.actorType();
        }
        return "SYSTEM";
    }

    private static String correlationId(TrustContext ctx) {
        if (ctx != null && ctx.correlationId() != null) {
            return ctx.correlationId().toString();
        }
        return UUID.randomUUID().toString();
    }
}
