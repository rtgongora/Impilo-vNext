package zw.gov.mohcc.impilo.coverage.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RemittanceResponse(
        UUID id,
        String payerId,
        String providerRef,
        BigDecimal amount,
        String currency,
        String status,
        String referenceNumber,
        OffsetDateTime createdAt
) {}
