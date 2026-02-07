package zw.gov.mohcc.impilo.tshepo.offline.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * Response containing an offline pack's metadata and signed content.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OfflinePackResponse(
        UUID id,
        UUID tenantId,
        UUID facilityId,
        String generatedFor,
        String signedPack,
        Instant generatedAt,
        Instant expiresAt,
        int version
) {}
