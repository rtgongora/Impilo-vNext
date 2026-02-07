package zw.gov.mohcc.impilo.tshepo.identity.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request to introspect a scoped token (check validity, expiry, revocation).
 */
public record IntrospectRequest(
        @NotBlank String token
) {}
