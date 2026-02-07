package zw.gov.mohcc.impilo.tshepo.consent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO representing a share link in API responses.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ShareLinkDto(
        UUID id,
        UUID tenantId,
        String patientRef,
        String createdBy,
        String scope,
        String purpose,
        int maxUses,
        int useCount,
        Instant expiresAt,
        Instant createdAt,
        boolean valid
) {
}
