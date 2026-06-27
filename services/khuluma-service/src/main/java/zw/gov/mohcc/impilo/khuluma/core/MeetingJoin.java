package zw.gov.mohcc.impilo.khuluma.core;

/**
 * A participant's media join for a meeting — the live-service event + the LiveKit room URL + token.
 * {@code mediaAvailable=false} (with {@code error}) means the lifecycle is fine but media could not
 * be minted (live-service/rtc-gateway/LiveKit down or unconfigured).
 */
public record MeetingJoin(
        String conversationId,
        String eventId,
        boolean mediaAvailable,
        String roomUrl,
        String accessToken,
        String provider,
        String error
) {}
