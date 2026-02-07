package zw.gov.mohcc.impilo.tshepo.authz.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * Device profile response for the device management API.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DeviceProfileResponse(
        Long id,
        UUID tenantId,
        String fingerprint,
        String actorId,
        int riskScore,
        boolean blocked,
        Instant lastSeenAt,
        Instant createdAt
) {}
