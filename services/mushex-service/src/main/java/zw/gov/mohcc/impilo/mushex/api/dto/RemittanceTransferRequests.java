package zw.gov.mohcc.impilo.mushex.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public final class RemittanceTransferRequests {

    private RemittanceTransferRequests() {
    }

    public record CreateRemittanceTransferRequest(
            @NotBlank String remittanceCode,
            @NotBlank String senderRef,
            @NotBlank String recipientRef,
            @NotBlank String recipientType,
            String originCountry,
            String destinationCountry,
            String domesticOrInternational,
            String remittancePurpose,
            String healthContextJson,
            @NotNull BigDecimal amount,
            String currency) {
    }
}
