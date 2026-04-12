package zw.gov.mohcc.impilo.wallet.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for adding a funding source to a wallet.
 */
public record AddFundingSourceRequest(
        @NotBlank @Size(max = 32) String sourceType,
        @Size(max = 64) String provider,
        @Size(max = 128) String accountRef,
        @Size(max = 255) String accountName
) {}
