package zw.gov.mohcc.impilo.rtc.model;

import java.time.Instant;
import java.util.Map;

public record RtcSessionResponse(
        String id,
        String provider,
        String roomName,
        String roomUrl,
        String accessToken,
        Instant tokenExpiresAt,
        String status,
        String channel,
        Map<String, Boolean> capabilities,
        Map<String, Object> mediaPolicy
) {
}
