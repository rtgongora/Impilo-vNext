package zw.gov.mohcc.impilo.madi.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.madi.events.MadiEventEmitter;
import zw.gov.mohcc.impilo.sharedkernel.security.UngovernedOverrideRecorder;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Records ungoverned break-glass overrides to MADI's event outbox.
 *
 * <h2>Why the outbox and not an audit-service call</h2>
 * <p>This record is written in exactly one situation: the trust plane could not be reached. Reaching
 * for a different remote service at that moment is reaching out during the conditions under which
 * remote calls are failing — a shared network fault, a cluster DNS problem or a node under pressure
 * takes out tshepo-authz and tshepo-audit together, and the record would be lost for the same reason
 * the grant check was. The outbox is a local database write in the caller's transaction, published
 * onward when the estate recovers.</p>
 *
 * <p>It also keeps the emergency path free of a second synchronous hop. A clinician releasing
 * O-negative blood should not wait on an audit round-trip.</p>
 *
 * <h2>Legible as an override, not as an access log line</h2>
 * <p>The event type is {@code BREAK_GLASS_UNGOVERNED_OVERRIDE} and the aggregate type is
 * {@code EMERGENCY_ACCESS} — deliberately its own pair rather than a generic audit event with a flag
 * in the payload, so the post-hoc review is a selection on event type and not a scan. The payload
 * carries {@code reviewRequired: true} and {@code reviewStatus: PENDING} so an unreviewed override is
 * findable as such.</p>
 */
@Service
public class OutboxUngovernedOverrideRecorder implements UngovernedOverrideRecorder {

    private static final Logger log = LoggerFactory.getLogger(OutboxUngovernedOverrideRecorder.class);

    /** The one event type a reviewer selects on. Changing this breaks the review query. */
    public static final String EVENT_TYPE = "BREAK_GLASS_UNGOVERNED_OVERRIDE";
    public static final String AGGREGATE_TYPE = "EMERGENCY_ACCESS";

    private final MadiEventEmitter eventEmitter;

    public OutboxUngovernedOverrideRecorder(MadiEventEmitter eventEmitter) {
        this.eventEmitter = eventEmitter;
    }

    @Override
    public void record(UngovernedOverride override) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", override.tenantId());
        payload.put("actorId", override.actorId());
        payload.put("action", override.action());
        payload.put("subjectRef", override.subjectRef());
        payload.put("purposeOfUse", override.purposeOfUse());
        payload.put("escalationGrantId", override.grantId());
        payload.put("governed", false);
        payload.put("reason", override.reason());
        // The review obligation travels with the record, so it cannot be inferred away later.
        payload.put("reviewRequired", true);
        payload.put("reviewStatus", "PENDING");

        // Deliberately not swallowed. An override that cannot be recorded must fail loudly: the
        // record is the reason this branch is permitted at all.
        java.util.UUID tenant = null;
        try {
            tenant = override.tenantId() == null ? null : java.util.UUID.fromString(override.tenantId());
        } catch (IllegalArgumentException ignored) {
            // A malformed tenant must not lose the record — the event still goes out, tenant null.
        }
        eventEmitter.emit(AGGREGATE_TYPE, override.subjectRef(), EVENT_TYPE,
                "PATIENT", override.subjectRef(), payload, tenant);

        log.error("UNGOVERNED BREAK-GLASS OVERRIDE recorded for review: action={} actor={} subject={} "
                        + "grant={} — emergency access proceeded WITHOUT a confirmed grant.",
                override.action(), override.actorId(), override.subjectRef(), override.grantId());
    }
}
