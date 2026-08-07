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
 * @param actorId    actor the grant must belong to; a grant never transfers between actors
 * @param grantToken optional {@code x-escalation-grant-id} the caller was handed, if any
 * @param action     optional label for the emergency action, recorded on the query for review
 */
public record BreakGlassGrantValidationRequest(

        @NotNull(message = "tenantId is required")
        UUID tenantId,

        @NotBlank(message = "actorId is required")
        @Size(max = 255)
        String actorId,

        @Size(max = 64)
        String grantToken,

        @Size(max = 64)
        String action
) {}
