package zw.gov.mohcc.impilo.tshepo.consent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO representing a consent directive in API responses.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConsentDirectiveDto(
        UUID id,
        UUID tenantId,
        String patientRef,
        String grantorRef,
        String granteeRef,
        String status,
        String scope,
        String purpose,
        String provision,
        Instant periodStart,
        Instant periodEnd,
        Instant createdAt,
        Instant updatedAt,
        Instant revokedAt,
        String revocationReason
) {
}
