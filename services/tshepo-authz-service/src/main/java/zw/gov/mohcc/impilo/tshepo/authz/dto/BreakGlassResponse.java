package zw.gov.mohcc.impilo.tshepo.authz.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * Response for a break-glass request (create, review, or list).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BreakGlassResponse(
        UUID id,
        UUID tenantId,
        String actorId,
        String reason,
        String resourceType,
        String resourceId,
        Boolean approved,
        String reviewStatus,
        Instant grantedAt,
        Instant expiresAt,
        String reviewedBy,
        Instant reviewedAt
) {}
