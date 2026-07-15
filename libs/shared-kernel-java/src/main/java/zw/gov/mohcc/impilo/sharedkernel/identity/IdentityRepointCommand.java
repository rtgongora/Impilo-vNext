package zw.gov.mohcc.impilo.sharedkernel.identity;

import java.util.Objects;

/**
 * The parsed intent of a {@code vito.merge.executed} event: repoint every row a participant
 * owns from the tombstoned {@code mergedHealthId} onto the surviving {@code survivorHealthId}.
 *
 * <p>Health IDs are VITO CPIDs (the patient anchor stamped across the trauma pipeline as
 * {@code patient_cpid}). When an unknown trauma patient's provisional identity is reconciled
 * to a confirmed identity via VITO {@code MergeService.merge}, this command tells each phase
 * owner (pct / inpatient / madi / daidzai …) to move its links so that zero rows are orphaned.</p>
 *
 * <p>Immutable value object; safe to log (identifiers only, no PII).</p>
 */
public record IdentityRepointCommand(
        String tenantId,
        String mergedHealthId,
        String survivorHealthId,
        String mergeId,
        String correlationId) {

    public IdentityRepointCommand {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(mergedHealthId, "mergedHealthId");
        Objects.requireNonNull(survivorHealthId, "survivorHealthId");
        if (mergedHealthId.equals(survivorHealthId)) {
            throw new IllegalArgumentException(
                    "mergedHealthId and survivorHealthId must differ: " + mergedHealthId);
        }
    }

    /**
     * Stable idempotency key for a single merge propagation, so a participant that has already
     * applied a repoint can no-op a redelivered event. Scoped to the (tenant, merge, from→to)
     * triple rather than the raw event id, which survives at-least-once redelivery and replays.
     */
    public String idempotencyKey() {
        return tenantId + ":" + mergeId + ":" + mergedHealthId + "->" + survivorHealthId;
    }
}
