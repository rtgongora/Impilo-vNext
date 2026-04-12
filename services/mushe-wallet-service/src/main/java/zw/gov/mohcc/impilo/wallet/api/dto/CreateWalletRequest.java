package zw.gov.mohcc.impilo.wallet.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating a new wallet.
 */
public record CreateWalletRequest(
        @NotBlank @Size(max = 32) String ownerType,
        @NotBlank @Size(max = 128) String ownerRef,
        @Size(max = 255) String displayName,
        @Size(max = 3) String currency
) {}
