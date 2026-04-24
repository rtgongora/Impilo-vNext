package zw.gov.mohcc.impilo.mushex.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public final class FinancialPlatformCardRequests {

    private FinancialPlatformCardRequests() {}

    public record CreateCardProfileRequest(
            String walletId,
            String ownerRef,
            @NotBlank String cardType,
            String tokenRef,
            String expiryMetaJson,
            String metadataJson) {
    }

    public record CreateReversalRecordRequest(
            @NotBlank String reversalCode,
            @NotBlank String originalTxnRef,
            @NotNull BigDecimal amount,
            String currency,
            String reason,
            String metadataJson) {
    }
}
