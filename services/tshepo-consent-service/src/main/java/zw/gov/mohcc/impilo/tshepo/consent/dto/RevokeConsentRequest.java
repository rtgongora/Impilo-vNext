package zw.gov.mohcc.impilo.tshepo.consent.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for revoking a consent directive.
 */
public record RevokeConsentRequest(
        @NotBlank(message = "reason is required")
        String reason
) {
}
