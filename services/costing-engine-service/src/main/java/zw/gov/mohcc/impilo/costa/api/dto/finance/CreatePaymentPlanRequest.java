package zw.gov.mohcc.impilo.costa.api.dto.finance;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreatePaymentPlanRequest(
        @NotBlank String patientCpid,
        @NotNull BigDecimal totalAmount,
        @NotNull BigDecimal installmentAmount,
        String frequency,
        LocalDate nextDueDate,
        @NotNull Integer installmentsTotal
) {}
