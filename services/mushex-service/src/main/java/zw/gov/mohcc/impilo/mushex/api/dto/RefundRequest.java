package zw.gov.mohcc.impilo.mushex.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record RefundRequest(
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        String reason
) {}
