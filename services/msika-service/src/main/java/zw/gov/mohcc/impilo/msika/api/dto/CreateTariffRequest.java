package zw.gov.mohcc.impilo.msika.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateTariffRequest(
    @NotBlank String tariffCode,
    @NotBlank String name,
    String description,
    String category,
    @NotNull BigDecimal unitPrice,
    String currency,
    String billingModel,
    @NotNull LocalDate effectiveFrom,
    LocalDate effectiveTo,
    Object pricingRules
) {}
