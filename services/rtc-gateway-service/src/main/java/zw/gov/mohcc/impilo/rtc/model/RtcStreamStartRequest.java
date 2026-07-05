package zw.gov.mohcc.impilo.rtc.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Start an RTMP stream-out for a session. {@code urls} are the restream
 * targets (rtmp:// or rtmps://) — provenance is the caller's responsibility;
 * the estate has no restream target infrastructure yet, so this API sits
 * behind {@code rtc.gateway.stream-out-enabled} (default false).
 */
public record RtcStreamStartRequest(
        @NotBlank String startedBy,
        @NotBlank String startedByRole,
        @NotEmpty List<String> urls,
        String layout
) {
}
