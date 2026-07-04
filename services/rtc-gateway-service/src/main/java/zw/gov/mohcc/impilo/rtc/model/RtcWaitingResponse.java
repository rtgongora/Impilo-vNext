package zw.gov.mohcc.impilo.rtc.model;

/**
 * Lobby response for a token request that did not mint a token:
 * {@code status} is WAITING (in the lobby, poll again after admit) or
 * DENIED (refused by a roomAdmin).
 */
public record RtcWaitingResponse(
        String status,
        String sessionId,
        String identity
) implements RtcSessionResult {

    public static RtcWaitingResponse waiting(String sessionId, String identity) {
        return new RtcWaitingResponse("WAITING", sessionId, identity);
    }

    public static RtcWaitingResponse denied(String sessionId, String identity) {
        return new RtcWaitingResponse("DENIED", sessionId, identity);
    }
}
