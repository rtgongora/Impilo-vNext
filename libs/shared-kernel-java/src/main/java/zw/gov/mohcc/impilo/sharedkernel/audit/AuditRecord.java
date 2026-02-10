package zw.gov.mohcc.impilo.sharedkernel.audit;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable audit record appended to the audit ledger for every
 * authorization decision, data mutation, or step-up event.
 *
 * @param actorId        who performed the action (user ID, service account, system)
 * @param actorType      type of actor (USER, SERVICE, SYSTEM)
 * @param action         the action performed (e.g. "PATIENT_MERGE", "PRESCRIBE")
 * @param decision       the outcome (ALLOW, DENY, STEP_UP_REQUIRED)
 * @param reasonCodes    policy reason codes explaining the decision
 * @param policyVersion  version of the policy set that was evaluated
 * @param tenantId       tenant scope
 * @param podId          originating pod/facility
 * @param correlationId  end-to-end correlation identifier
 * @param occurredAt     when the decision was made
 * @param resource       the resource acted upon (domain-specific map)
 */
public record AuditRecord(
        String actorId,
        String actorType,
        String action,
        String decision,
        List<String> reasonCodes,
        String policyVersion,
        String tenantId,
        String podId,
        String correlationId,
        OffsetDateTime occurredAt,
        Map<String, Object> resource
) {

    public AuditRecord {
        Objects.requireNonNull(actorId, "actor_id is required");
        Objects.requireNonNull(actorType, "actor_type is required");
        Objects.requireNonNull(action, "action is required");
        Objects.requireNonNull(decision, "decision is required");
        Objects.requireNonNull(tenantId, "tenant_id is required");
        Objects.requireNonNull(podId, "pod_id is required");
        Objects.requireNonNull(correlationId, "correlation_id is required");
        Objects.requireNonNull(occurredAt, "occurred_at is required");
        reasonCodes = reasonCodes != null ? List.copyOf(reasonCodes) : List.of();
        resource = resource != null ? Map.copyOf(resource) : Map.of();
    }
}
