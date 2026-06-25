package zw.gov.mohcc.impilo.khuluma.core;

import zw.gov.mohcc.impilo.khuluma.domain.CallEntity;

/**
 * Outcome of a call command: the call record plus the requesting actor's media join
 * details. {@code mediaAvailable=false} (with {@code mediaError}) means the lifecycle
 * succeeded but rtc-gateway/LiveKit could not mint a token (unconfigured or consent-gated).
 */
public record CallResult(
        CallEntity call,
        boolean mediaAvailable,
        String roomName,
        String roomUrl,
        String accessToken,
        String provider,
        String mediaError
) {}
