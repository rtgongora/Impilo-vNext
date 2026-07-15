package zw.gov.mohcc.impilo.sharedkernel.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Predicate;

/**
 * Framework-agnostic handler for the {@code vito.merge.executed} event — the propagation
 * channel that keeps trauma links consistent when two identities are reconciled.
 *
 * <p>Today {@code vito.merge.executed} has no consumers: when VITO merges an unknown trauma
 * patient's provisional identity into a confirmed one, nothing repoints the downstream links,
 * leaving orphaned rows keyed on the tombstoned Health ID. This handler is the missing channel.
 * It is deliberately Spring-free (shared-kernel-java carries no web/Kafka deps); each service
 * wires it into its own consumer — a Kafka {@code @KafkaListener}, or the no-Kafka outbox
 * drainer used by the runtime-proof rigs — and passes the raw event payload to {@link #handle}.</p>
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Parse the merge payload into an {@link IdentityRepointCommand}
 *       (fields {@code tenantId}, {@code survivorHealthId}, {@code mergedHealthId}).</li>
 *   <li>Idempotency: skip when {@code alreadyProcessed} reports the merge key was seen, so a
 *       redelivered/replayed event never double-repoints.</li>
 *   <li>Delegate to the participant's {@link IdentityRepointHook} and return the row count.</li>
 * </ul>
 *
 * <p>W0 wires a {@link IdentityRepointHook#noop(String) no-op hook}; W6 replaces it per service
 * with real, audited repointing across that service's trauma tables.</p>
 */
public final class IdentityRepointHandler {

    /** Event type this handler responds to. */
    public static final String EVENT_TYPE = "vito.merge.executed";

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
                hook.participant(), command.mergeId(), command.mergedHealthId(),
                command.survivorHealthId(), rows);
        return new RepointOutcome(hook.participant(), command, rows, false);
    }

    /**
     * Parse the VITO merge payload. Accepts either the flat payload emitted by
     * {@code MergeService.publishEvent} or a v1.1 EventEnvelope whose {@code payload} object
     * carries the same fields.
     */
    static IdentityRepointCommand parse(String eventPayloadJson) {
        try {
            JsonNode root = MAPPER.readTree(eventPayloadJson);
            JsonNode body = root.has("survivorHealthId") ? root
                    : root.path("payload"); // unwrap an EventEnvelope if present
            String tenantId = text(body, "tenantId");
            String survivorHealthId = text(body, "survivorHealthId");
            String mergedHealthId = text(body, "mergedHealthId");
            String mergeId = body.has("mergeId") ? text(body, "mergeId")
                    : text(root, "aggregateId");
            String correlationId = root.has("correlationId") ? text(root, "correlationId") : null;
            if (survivorHealthId == null || mergedHealthId == null || tenantId == null) {
                throw new IllegalArgumentException(
                        "vito.merge.executed payload missing tenantId/survivorHealthId/mergedHealthId");
            }
            return new IdentityRepointCommand(tenantId, mergedHealthId, survivorHealthId,
                    mergeId, correlationId);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalArgumentException("unparseable vito.merge.executed payload", e);
        }
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
