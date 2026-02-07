package zw.gov.mohcc.impilo.tshepo.consent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request body for creating a consent directive from a FHIR R4 Consent resource.
 * The fhirConsentJson field must contain a valid FHIR R4 Consent resource JSON string.
 */
public record CreateConsentRequest(
        @NotNull(message = "tenantId is required")
        UUID tenantId,

        @NotBlank(message = "patientRef is required")
        String patientRef,

        @NotBlank(message = "grantorRef is required")
        String grantorRef,

        String granteeRef,

        @NotBlank(message = "scope is required")
        String scope,

        @NotBlank(message = "purpose is required")
        String purpose,

        String provision,

        @NotBlank(message = "fhirConsentJson is required")
        String fhirConsentJson
) {
}
