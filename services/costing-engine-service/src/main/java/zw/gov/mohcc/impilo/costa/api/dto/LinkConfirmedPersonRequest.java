package zw.gov.mohcc.impilo.costa.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * Link a provisional emergency person to the confirmed person resolved post-stabilisation.
 * VITO remains the identity SoR; this records the link, it does not overwrite the person.
 */
public record LinkConfirmedPersonRequest(
        @JsonProperty("confirmed_person_ref")
        @NotBlank(message = "confirmed_person_ref is required")
        String confirmedPersonRef,
        @JsonProperty("reason") String reason
) {}
