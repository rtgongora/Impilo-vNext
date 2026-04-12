package zw.gov.mohcc.impilo.wallet.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Request body for placing a hold on a wallet.
 */
public record PlaceHoldRequest(
        @NotNull UUID walletId,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @Size(max = 255) String reason,
        @Size(max = 255) String reference,
        @NotNull OffsetDateTime expiresAt
) {}
