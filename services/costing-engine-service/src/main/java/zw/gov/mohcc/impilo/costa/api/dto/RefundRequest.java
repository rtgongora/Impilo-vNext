package zw.gov.mohcc.impilo.costa.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record RefundRequest(
        @NotNull @Positive BigDecimal amount,
        @NotNull String reason,
        String reasonCode,
        String refundType
) {}
