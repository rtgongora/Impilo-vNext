package zw.gov.mohcc.impilo.wallet.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request body for wallet-to-wallet transfer.
 */
public record TransferRequest(
        @NotNull UUID fromWalletId,
        @NotNull UUID toWalletId,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @Size(max = 255) String reference,
        @Size(max = 512) String description,
        @Size(max = 32) String channel
) {}
