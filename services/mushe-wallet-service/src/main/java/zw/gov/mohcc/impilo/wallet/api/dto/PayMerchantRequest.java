package zw.gov.mohcc.impilo.wallet.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request body for paying a merchant from an individual wallet.
 */
public record PayMerchantRequest(
        @NotNull UUID fromWalletId,
        @NotBlank @Size(max = 128) String merchantProviderNumber,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @Size(max = 255) String reference,
        @Size(max = 32) String channel
) {}
