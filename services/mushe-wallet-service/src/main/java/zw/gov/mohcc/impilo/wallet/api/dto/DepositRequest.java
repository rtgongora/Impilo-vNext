package zw.gov.mohcc.impilo.wallet.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request body for depositing from a linked funding source.
 */
public record DepositRequest(
        @NotNull UUID sourceId,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @Size(max = 255) String reference
) {}
