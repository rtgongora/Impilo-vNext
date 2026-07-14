package zw.gov.mohcc.impilo.coverage.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * Health-worker subsidy self opt-in. The health id is verified SERVER-SIDE
 * against VARAPI/Vashandi recognition — the request carries no self-asserted
 * recognition claim.
 */
public record HealthWorkerOptInRequest(
        @JsonProperty("health_id")
        @NotBlank(message = "health_id is required")
        String healthId
) {}
