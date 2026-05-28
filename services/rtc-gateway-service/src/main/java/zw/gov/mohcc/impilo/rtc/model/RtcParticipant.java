package zw.gov.mohcc.impilo.rtc.model;

import jakarta.validation.constraints.NotBlank;

public record RtcParticipant(
        @NotBlank String identity,
        String displayName,
        @NotBlank String role
) {
}
