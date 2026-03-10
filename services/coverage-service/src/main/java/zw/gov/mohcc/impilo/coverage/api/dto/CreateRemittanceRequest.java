package zw.gov.mohcc.impilo.coverage.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CreateRemittanceRequest(
        @NotBlank String payerId,
        @NotBlank String providerRef,
        @NotNull BigDecimal amount,
        String currency,
        String referenceNumber,
        String lineItems,
        String notes
) {}
