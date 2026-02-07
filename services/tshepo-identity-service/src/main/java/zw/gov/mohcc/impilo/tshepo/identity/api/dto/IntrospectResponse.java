package zw.gov.mohcc.impilo.tshepo.identity.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Token introspection result.
 */
public record IntrospectResponse(
        boolean active,
        String jti,
        UUID tenantId,
        String actorId,
        String scope,
        String targetService,
        String subjectRef,
        Instant expiresAt
) {}
