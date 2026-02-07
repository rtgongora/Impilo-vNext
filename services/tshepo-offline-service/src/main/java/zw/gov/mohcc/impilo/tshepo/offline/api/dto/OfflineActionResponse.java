package zw.gov.mohcc.impilo.tshepo.offline.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * Response for a logged offline action.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OfflineActionResponse(
        Long id,
        UUID tenantId,
        UUID capabilityTokenId,
        String actorId,
        String actionType,
        String resourceType,
        String resourceRef,
        UUID oCpid,
        Instant performedAt,
        String syncStatus
) {}
