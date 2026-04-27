package zw.gov.mohcc.impilo.notification.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * @param patientRef optional FHIR-style or CPID — enables Mvumo communication-preference gating
 * @param messageKind e.g. REMINDER, SENSITIVE — forwarded to {@code /communication-preferences/evaluate}
 */
public record NotifyRequest(
        @Size(max = 128) String templateKey,
        @NotBlank @Size(max = 32) String channel,
        @NotBlank @Size(max = 256) @JsonProperty("to") String recipient,
        Map<String, String> variables,
        @Size(max = 300) @JsonProperty("patientRef") String patientRef,
        @Size(max = 64) String messageKind) {
    public NotifyRequest {
        if (variables == null) {
            variables = Map.of();
        }
    }
}
