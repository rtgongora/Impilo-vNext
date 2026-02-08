package zw.gov.mohcc.impilo.mushex.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateIntentRequest(
        @NotNull String sourceType,
        @NotBlank String sourceId,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        String currency,
        String facilityId,
        @NotBlank String idempotencyKey,
        String metadata
) {}
