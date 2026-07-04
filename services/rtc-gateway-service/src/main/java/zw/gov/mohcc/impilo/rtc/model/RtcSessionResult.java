package zw.gov.mohcc.impilo.rtc.model;

/**
 * Outcome of a token-issuing call: either a full {@link RtcSessionResponse}
 * (token minted) or an {@link RtcWaitingResponse} (lobby-gated participant is
 * WAITING or DENIED — no token). Serialized as the concrete record.
 */
public sealed interface RtcSessionResult permits RtcSessionResponse, RtcWaitingResponse {
}
