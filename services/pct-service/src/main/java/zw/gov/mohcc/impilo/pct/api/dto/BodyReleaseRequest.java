package zw.gov.mohcc.impilo.pct.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Authorise release of a body to a named recipient (WS#8). Blocked while a legal / coroner /
 * post-mortem hold is active. Release is recorded only to an authorised, named recipient.
 */
public record BodyReleaseRequest(
        @NotBlank String releasedToName,
        String releasedToRelationship,
        String releasedToIdRef
) {}
