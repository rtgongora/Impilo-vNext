package zw.gov.mohcc.impilo.sharedkernel.security;

import java.util.Objects;

/**
 * Everything a guard knows about the emergency action it is about to permit or refuse.
 *
 * <p>Carried as one object rather than a parameter list so that adding a dimension later does not
 * silently change the meaning of an existing call site's positional arguments — the kind of edit
 * that reorders {@code actorId} and {@code subjectRef} without a compiler error.</p>
 *
 * @param tenantId     tenant the action happens in; a grant never crosses tenants
 * @param actorId      actor performing the action; a grant never transfers between actors
 * @param grantToken   the {@code x-escalation-grant-id} the caller was handed, if any
 * @param purposeOfUse the purpose-of-use asserted by the caller — <b>client-supplied, and by itself
 *                     worth nothing</b>; it selects whether an emergency path is being attempted,
 *                     it does not authorise one
 * @param action       what is being attempted, e.g. {@code O_NEG_EMERGENCY_RELEASE}
 * @param subjectRef   the patient the action is for
 */
public record BreakGlassGrantQuery(
        String tenantId,
        String actorId,
        String grantToken,
        String purposeOfUse,
        String action,
        String subjectRef
) {

    public BreakGlassGrantQuery {
        Objects.requireNonNull(action, "action is required");
    }
}
