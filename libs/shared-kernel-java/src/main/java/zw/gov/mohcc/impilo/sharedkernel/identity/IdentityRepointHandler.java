package zw.gov.mohcc.impilo.sharedkernel.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Predicate;

/**
 * Framework-agnostic handler for clinical-plane subject repoint events — the propagation
 * channel that keeps clinical links consistent when two identities are reconciled.
 *
 * <p>Per the Identity Contract §7.3, clinical services consume
 * {@code impilo.identity.subject.merged.v1} (identity merge) and
 * {@code impilo.identity.subject.reconciled.v1} (offline O-CPID reconcile) — both are
 * "repoint old CPID → new CPID" commands. This handler is deliberately Spring-free
 * (shared-kernel-java carries no web/Kafka deps); each service wires it into its own
 * consumer — a Kafka {@code @KafkaListener}, or the no-Kafka outbox drainer used by the
 * runtime-proof rigs — and passes the raw event payload to {@link #handle}.</p>
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Parse the subject payload into an {@link IdentityRepointCommand}
 *       (fields {@code tenantId}, {@code oldSubjectCpid}, {@code newSubjectCpid}).</li>
 *   <li>Idempotency: skip when {@code alreadyProcessed} reports the merge key was seen, so a
 *       redelivered/replayed event never double-repoints.</li>
 *   <li>Delegate to the participant's {@link IdentityRepointHook} and return the row count.</li>
 * </ul>
 */
public final class IdentityRepointHandler {

    /** Merge event type this handler responds to. */
    public static final String EVENT_TYPE = "impilo.identity.subject.merged.v1";

    /** Offline-reconcile event type this handler responds to (same repoint semantics). */
    public static final String RECONCILED_EVENT_TYPE = "impilo.identity.subject.reconciled.v1";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final IdentityRepointHook hook;
    private final Predicate<IdentityRepointCommand> alreadyProcessed;
    private final Logger log;

    /**
     * @param hook             the participant's repoint implementation (never null).
     * @param alreadyProcessed idempotency guard — returns {@code true} if this merge has already
     *                         been applied by this participant. Pass {@code cmd -> false} to
     *                         always attempt (when the hook itself is fully idempotent).
     */
    public IdentityRepointHandler(IdentityRepointHook hook,
                                  Predicate<IdentityRepointCommand> alreadyProcessed) {
        if (hook == null) {
            throw new IllegalArgumentException("hook must not be null");
        }
        this.hook = hook;
        this.alreadyProcessed = alreadyProcessed != null ? alreadyProcessed : cmd -> false;
        this.log = LoggerFactory.getLogger("IdentityRepointHandler." + hook.participant());
    }

    /**
     * Handle a raw {@code vito.merge.executed} event payload (canonical JSON).
     *
     * @return the repoint outcome; {@code skippedDuplicate=true} when the guard reports the
     *         merge was already applied.
     * @throws IllegalArgumentException if the payload is missing required merge fields.
     */
    public RepointOutcome handle(String eventPayloadJson) {
        IdentityRepointCommand command = parse(eventPayloadJson);
        return handle(command);
    }

    /** Handle an already-parsed command (for callers that decode the envelope themselves). */
    public RepointOutcome handle(IdentityRepointCommand command) {
        if (alreadyProcessed.test(command)) {
            log.debug("skip already-applied identity repoint for {}: {}",
                    hook.participant(), command.idempotencyKey());
            return new RepointOutcome(hook.participant(), command, 0, true);
        }
        int rows = hook.repoint(command);
        log.info("identity repoint applied by {} for merge {} ({}→{}): {} row(s)",
                hook.participant(), command.mergeId(), command.oldSubjectCpid(),
                command.newSubjectCpid(), rows);
        return new RepointOutcome(hook.participant(), command, rows, false);
    }

    /**
     * Parse a clinical-plane subject repoint event (Identity Contract §7.3):
     * {@code impilo.identity.subject.merged.v1} carrying
     * {@code survivor_cpid}/{@code merged_cpid}, or
     * {@code impilo.identity.subject.reconciled.v1} carrying
     * {@code canonical_cpid}/{@code provisional_cpid}. Both repoint
     * old&nbsp;→&nbsp;new the same way. Accepts a bare payload or a v1.1
     * EventEnvelope whose {@code payload} object carries the fields. Health-ID
     * shaped fields are deliberately NOT accepted — clinical services never see
     * Health IDs.
     */
    static IdentityRepointCommand parse(String eventPayloadJson) {
        try {
            JsonNode root = MAPPER.readTree(eventPayloadJson);
            if (root != null && root.isTextual()) {
                root = MAPPER.readTree(root.asText()); // unwrap double-encoded wire values
            }
            JsonNode body = (root.has("survivor_cpid") || root.has("canonical_cpid"))
                    ? root
                    : root.path("payload"); // unwrap an EventEnvelope if present
            String tenantId = firstText(body, "tenant_id", "tenantId");
            if (tenantId == null) {
                tenantId = firstText(root, "tenant_id", "tenantId");
            }
            // merged: survivor/merged; reconciled: canonical/provisional — old → new either way
            String newSubjectCpid = firstText(body, "survivor_cpid", "canonical_cpid");
            String oldSubjectCpid = firstText(body, "merged_cpid", "provisional_cpid");
            String mergeId = body.has("mergeId") ? text(body, "mergeId")
                    : firstText(root, "event_id", "aggregateId");
            String correlationId = firstText(root, "correlation_id", "correlationId");
            if (newSubjectCpid == null || oldSubjectCpid == null || tenantId == null) {
                throw new IllegalArgumentException(
                        "subject repoint payload missing tenant_id/survivor_cpid/merged_cpid");
            }
            return new IdentityRepointCommand(tenantId, oldSubjectCpid, newSubjectCpid,
                    mergeId, correlationId);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalArgumentException("unparseable subject repoint payload", e);
        }
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String v = text(node, field);
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return (v == null || v.isNull()) ? null : v.asText();
    }

    /** Outcome of dispatching one merge to one participant. */
    public record RepointOutcome(
            String participant,
            IdentityRepointCommand command,
            int rowsRepointed,
            boolean skippedDuplicate) {

        public boolean applied() {
            return !skippedDuplicate;
        }
    }
}
