package zw.gov.mohcc.impilo.tshepo.identity.api.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Token introspection result.
 *
 * <p>{@code tokenKind} + {@code contextClaims} let a consuming service (the PDP)
 * read a WORK_CONTEXT token's operational context — facility / department / ward /
 * workspace / programme / org / provider / role — without re-verifying the JWS or
 * holding the signing key. Server-side state (active/revoked/expired) is already
 * enforced here, so the consumer can treat these claims as duty-authoritative.</p>
 */
public record IntrospectResponse(
        boolean active,
        String jti,
        UUID tenantId,
        String actorId,
        String scope,
        String targetService,
        String subjectRef,
        Instant expiresAt,
        String tokenKind,
        Map<String, Object> contextClaims
) {}
