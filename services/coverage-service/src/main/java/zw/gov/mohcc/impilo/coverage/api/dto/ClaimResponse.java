package zw.gov.mohcc.impilo.coverage.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ClaimResponse(
        UUID id,
        UUID coverageId,
        UUID preauthId,
        String facilityId,
        String providerId,
        String encounterId,
        String claimType,
        String status,
        BigDecimal totalAmount,
        BigDecimal approvedAmount,
        OffsetDateTime submittedAt,
        OffsetDateTime adjudicatedAt
) {}
