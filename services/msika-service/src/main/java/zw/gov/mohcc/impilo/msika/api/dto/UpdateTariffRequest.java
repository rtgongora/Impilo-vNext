package zw.gov.mohcc.impilo.msika.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateTariffRequest(
    String name,
    String description,
    String category,
    BigDecimal unitPrice,
    String currency,
    String billingModel,
    LocalDate effectiveFrom,
    LocalDate effectiveTo,
    String status,
    Object pricingRules
) {}
