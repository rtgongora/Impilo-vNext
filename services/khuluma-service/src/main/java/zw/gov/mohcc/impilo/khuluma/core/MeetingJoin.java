package zw.gov.mohcc.impilo.khuluma.core;

/**
 * A participant's media join for a meeting — the live-service event + the LiveKit room URL + token.
 *
 * <p>{@code status} is the MEETING lobby outcome:
 * <ul>
 *   <li>{@code READY} — token minted, join the room;</li>
 *   <li>{@code WAITING} — knocking; a host/cohost must admit before a token is issued (poll join);</li>
 *   <li>{@code DENIED} — refused by a host/cohost;</li>
 *   <li>{@code UNAVAILABLE} — lifecycle fine but media could not be minted (live-service/
 *       rtc-gateway/LiveKit down or unconfigured), see {@code error}.</li>
 * </ul>
 */
public record MeetingJoin(
        String conversationId,
        String eventId,
        String status,
        String role,
        String rtcSessionId,
        boolean mediaAvailable,
        String roomUrl,
        String accessToken,
        String provider,
        String error
) {
    public static final String STATUS_READY = "READY";
    public static final String STATUS_WAITING = "WAITING";
    public static final String STATUS_DENIED = "DENIED";
    public static final String STATUS_UNAVAILABLE = "UNAVAILABLE";
}
