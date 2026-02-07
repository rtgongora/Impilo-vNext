package zw.gov.mohcc.impilo.tshepo.consent.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for updating an existing consent directive.
 * The FHIR Consent JSON is re-validated on update.
 */
public record UpdateConsentRequest(
        String granteeRef,
        String scope,
        String purpose,
        String provision,
        @NotBlank(message = "fhirConsentJson is required")
        String fhirConsentJson
) {
}
