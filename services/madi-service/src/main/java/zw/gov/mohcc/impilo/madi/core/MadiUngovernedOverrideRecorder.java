package zw.gov.mohcc.impilo.madi.core;

import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.madi.events.MadiEventEmitter;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.sharedkernel.security.UngovernedOverride;
import zw.gov.mohcc.impilo.sharedkernel.security.UngovernedOverrideRecorder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Records an ungoverned break-glass override into madi's transactional outbox, shaped so the audit
 * plane can ingest it.
 *
 * <h2>Why the outbox and not an HTTP call to the audit service</h2>
 *
 * <p>This runs at exactly the moment the network has been shown to be unreliable — the grant check
 * just failed to reach tshepo-authz. Making the record-of-last-resort depend on a second
 * synchronous call to a second remote service points the dependency the wrong way: the most likely
 * time to lose the record would be the time it matters most.</p>
 *
 * <p>The outbox row is written to madi's own database in the same transaction as the clinical
 * action it describes, so the record and the act commit or fail together — there is no window in
 * which the release happened and the record did not. Rows are marked published rather than deleted,
 * so the outbox remains a durable, queryable register even if Kafka never drains.</p>
 *
 * <p>Measured before choosing this: the only HTTP writer to {@code /v1/audit/events} in the estate
 * is experience-bff, which has a client-credentials {@code ServiceTokenProvider}. madi has none, and
 * workstream A made that endpoint {@code anyRequest().authenticated()} with an attributable trust
 * context, so the HTTP audit path does not work for this caller today. The Kafka path does — madi
 * already produces on it, and {@code AuditKafkaConsumer} ingests {@code tshepo.audit.events} into
 * the hash chain.</p>
 *
 * <h2>What is guaranteed, and what is best-effort</h2>
 *
 * <p><b>Guaranteed:</b> the local outbox row. It is transactional with the clinical write and
 * survives any downstream failure.</p>
 *
 * <p><b>Best-effort:</b> arrival in tshepo-audit's hash chain, which depends on the poller draining
 * and on that consumer running. The distinction is stated rather than glossed, because a reviewer
 * looking only at the audit chain could otherwise conclude no overrides occurred when the real
 * answer is in madi's outbox.</p>
 *
 * <h2>Legibility</h2>
 *
 * <p>The event type is {@link UngovernedOverride#EVENT_TYPE}, distinct from madi's ordinary
 * {@code EMERGENCY_RELEASE_BREAK_GLASS}. That separation is the point: a governed break-glass
 * release and an override nobody was able to check must not land in the same bucket, or the second
 * becomes unfindable among the first. The outcome is {@code ALLOW_UNGOVERNED} rather than the usual
 * {@code ALLOW}, so a query over the audit chain can select these without knowing madi exists.</p>
 */
@Component
public class MadiUngovernedOverrideRecorder implements UngovernedOverrideRecorder {

    /** Distinct from ALLOW so the audit plane can select overrides without service-specific knowledge. */
    static final String OUTCOME = "ALLOW_UNGOVERNED";

    /** Routed to {@code tshepo.audit.events} by {@code MadiOutboxPublisher}. */
    public static final String AGGREGATE_TYPE = "BREAK_GLASS_OVERRIDE";

    private final MadiEventEmitter eventEmitter;

    public MadiUngovernedOverrideRecorder(MadiEventEmitter eventEmitter) {
        this.eventEmitter = eventEmitter;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Deliberately does not catch. A failure here means madi's own database is unavailable, in
     * which case the clinical write this guards would fail moments later anyway — so refusing costs
     * no care that would otherwise have been delivered, while swallowing would produce an override
     * with no trace.</p>
     */
    @Override
    public void record(UngovernedOverride override) {
        TrustContext ctx = trustContext();

        // Shaped as tshepo-audit's AuditEventRequest so AuditKafkaConsumer can append it to the
        // hash chain directly. That record is @JsonIgnoreProperties(ignoreUnknown = true), so the
        // extra detail below is carried rather than rejected.
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

        eventEmitter.emit(
                AGGREGATE_TYPE,
                override.actorId() + ":" + override.action(),
                UngovernedOverride.EVENT_TYPE,
                "PATIENT",
                override.subjectRef() == null ? "UNKNOWN" : override.subjectRef(),
                payload,
                UUID.fromString(override.tenantId()));
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
     * guessing {@code PROVIDER}: an override whose actor type was invented reads as evidence about
     * who acted, and it would not be.
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
