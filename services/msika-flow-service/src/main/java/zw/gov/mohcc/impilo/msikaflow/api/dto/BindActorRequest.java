package zw.gov.mohcc.impilo.msikaflow.api.dto;

import jakarta.validation.constraints.NotBlank;

/** Body for POST /v1/vendors/{id}/bind-actor — bind a JWT principal to a vendor. */
public record BindActorRequest(
        @NotBlank String actorId
) {}
