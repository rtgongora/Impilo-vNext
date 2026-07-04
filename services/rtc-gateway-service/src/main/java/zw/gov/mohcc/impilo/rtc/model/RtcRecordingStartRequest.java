package zw.gov.mohcc.impilo.rtc.model;

import jakarta.validation.constraints.NotBlank;

public record RtcRecordingStartRequest(
        @NotBlank String startedBy,
        @NotBlank String startedByRole,
        String layout
) {
}
