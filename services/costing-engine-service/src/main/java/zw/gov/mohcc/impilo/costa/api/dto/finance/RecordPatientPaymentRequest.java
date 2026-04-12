package zw.gov.mohcc.impilo.costa.api.dto.finance;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record RecordPatientPaymentRequest(
        String billId,
        @NotNull BigDecimal amount,
        String paymentMethod,
        String paymentRef
) {}
