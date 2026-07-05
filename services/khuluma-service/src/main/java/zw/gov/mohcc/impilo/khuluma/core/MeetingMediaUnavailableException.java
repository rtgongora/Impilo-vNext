package zw.gov.mohcc.impilo.khuluma.core;

/**
 * The meeting's media transport (rtc-gateway) could not serve a lobby/media command right now —
 * the caller should retry. Maps to 503 at the API layer (distinct from IllegalStateException,
 * which Khuluma reserves for missing trust context → 401).
 */
public class MeetingMediaUnavailableException extends RuntimeException {
    public MeetingMediaUnavailableException(String message) {
        super(message);
    }
}
