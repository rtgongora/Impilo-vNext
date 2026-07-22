package zw.gov.mohcc.impilo.notification.api.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * @param patientRef optional FHIR-style or CPID — enables Mvumo communication-preference gating
 * @param messageKind e.g. REMINDER, SENSITIVE — forwarded to {@code /communication-preferences/evaluate}
 * @param notBefore optional future send time (TM-B9/B1). NULL = dispatch immediately;
 *                  a future instant holds the notification PENDING until due. Accepts JSON
 *                  keys {@code notBefore}, {@code scheduledAt}, or {@code not_before}.
 */
public record NotifyRequest(
        @Size(max = 128) String templateKey,
        @NotBlank @Size(max = 32) String channel,
        @NotBlank @Size(max = 256) @JsonProperty("to") String recipient,
        Map<String, String> variables,
        @Size(max = 300) @JsonProperty("patientRef") String patientRef,
        @Size(max = 64) String messageKind,
        @Size(max = 256) @JsonProperty("inboxRecipient") String inboxRecipient,
        @JsonProperty("notBefore") @JsonAlias({"scheduledAt", "not_before"}) OffsetDateTime notBefore) {
    public NotifyRequest {
        if (variables == null) {
            variables = Map.of();
        }
    }
}
