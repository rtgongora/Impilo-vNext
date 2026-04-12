package zw.gov.mohcc.impilo.wallet.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Request body for registering a new merchant (provider-as-merchant).
 */
public record RegisterMerchantRequest(
        @NotBlank @Size(max = 128) String providerNumber,
        UUID facilityId,
        @NotBlank @Size(max = 255) String merchantName,
        @Size(max = 32) String merchantType,
        @Size(max = 128) String settlementAccount,
        @Size(max = 64) String settlementBank
) {}
