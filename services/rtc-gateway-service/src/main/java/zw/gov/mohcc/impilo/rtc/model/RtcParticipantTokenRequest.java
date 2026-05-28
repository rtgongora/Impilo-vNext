package zw.gov.mohcc.impilo.rtc.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record RtcParticipantTokenRequest(
        @NotNull @Valid RtcParticipant participant
) {
}
