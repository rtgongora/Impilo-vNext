package zw.gov.mohcc.impilo.costa.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import zw.gov.mohcc.impilo.costa.domain.enums.PaymentType;

import java.math.BigDecimal;

public record PaymentIntentRequest(
        @NotNull PaymentType paymentType,
        @NotNull @Positive BigDecimal amount
) {}
