package zw.gov.mohcc.impilo.rtc.model;

import java.time.Instant;
import java.util.Map;

/**
 * Session/token success response. Field names are a frozen wire contract
 * (the BFF parses roomUrl/accessToken today): fields may be ADDED, never
 * renamed or removed. {@code mediaProfile} is set on token-minting responses
 * (FULL or AUDIO_ONLY), null on token-less reads (get/end).
 */
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
        Map<String, Object> mediaPolicy,
        String mediaProfile
) implements RtcSessionResult {
}
