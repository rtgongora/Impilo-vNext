package zw.gov.mohcc.impilo.wallet.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Request body for debiting a wallet.
 */
public record DebitWalletRequest(
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @NotBlank @Size(max = 32) String txnType,
        @Size(max = 32) String channel,
        @Size(max = 255) String reference,
        @Size(max = 512) String description,
        @Size(max = 255) String counterpartyRef,
        @Size(max = 255) String counterpartyName
) {}
