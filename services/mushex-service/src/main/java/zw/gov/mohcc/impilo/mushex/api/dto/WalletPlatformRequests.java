package zw.gov.mohcc.impilo.mushex.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public final class WalletPlatformRequests {

    private WalletPlatformRequests() {
    }

    public record CreateWalletPlatformRequest(
            @NotBlank String ownerType,
            @NotBlank String ownerRef,
            String currency,
            String walletType) {
    }

    public record WalletMovementRequest(
            @NotNull BigDecimal amount,
            String transactionType,
            String referenceCode) {
    }
}
