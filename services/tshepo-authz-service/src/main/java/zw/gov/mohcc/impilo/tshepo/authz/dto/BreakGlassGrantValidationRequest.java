package zw.gov.mohcc.impilo.tshepo.authz.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * A downstream service asking whether a named actor currently holds a break-glass grant.
 *
 * <p>The caller names the actor it is about to act as; it does not name a permission, a resource
 * scope or a role. That asymmetry is deliberate — see
 * {@link zw.gov.mohcc.impilo.tshepo.authz.api.BreakGlassGrantValidationController} for why this
 * request shape is what keeps the endpoint from drifting into a general authorization API.</p>
 *
 * @param tenantId   tenant the grant must belong to; a grant never crosses tenants
 * <p><b>There is deliberately no actorId.</b> The actor is the bearer's subject, taken server-side.
 * An actor in the body made this an oracle: any authenticated caller could ask whether a named
 * clinician holds a live break-glass grant. A caller may only ask about themselves.</p>
 *
 * @param grantToken optional {@code x-escalation-grant-id} the caller was handed, if any
 * @param action     optional label for the emergency action, recorded on the query for review
 */
public record BreakGlassGrantValidationRequest(

        @NotNull(message = "tenantId is required")
        UUID tenantId,

        @Size(max = 64)
        String grantToken,

        @Size(max = 64)
        String action
) {}
