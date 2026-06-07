package zw.gov.mohcc.impilo.live.media;

import java.time.Instant;

public record MediaTokenResult(
        String roomId,
        String roomUrl,
        String accessToken,
        Instant tokenExpiresAt,
        String provider,
        String channel
) {
}
