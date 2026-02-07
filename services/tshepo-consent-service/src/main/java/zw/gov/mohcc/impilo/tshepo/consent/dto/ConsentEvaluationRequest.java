package zw.gov.mohcc.impilo.tshepo.consent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Query parameters for consent evaluation. Maps to the GET /v1/consent/evaluate endpoint.
 */
public record ConsentEvaluationRequest(
        @NotNull(message = "tenantId is required")
        UUID tenantId,

        @NotBlank(message = "actorId is required")
        String actorId,

        @NotBlank(message = "subjectRef is required")
        String subjectRef,

        @NotBlank(message = "purpose is required")
        String purpose,

        @NotBlank(message = "scope is required")
        String scope
) {
}
