package zw.gov.mohcc.impilo.coverage.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record MemberContributionResponse(
        UUID id,
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal amount,
        String currency,
        String status,
        OffsetDateTime paidAt,
        String paymentRef) {}
