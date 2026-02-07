package zw.gov.mohcc.impilo.tshepo.offline.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response containing an issued or retrieved offline capability token.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CapabilityTokenResponse(
        UUID id,
        UUID tenantId,
        String actorId,
        UUID facilityId,
        String deviceFingerprint,
        List<String> capabilities,
        String signedToken,
        Instant issuedAt,
        Instant expiresAt,
        String status
) {}
