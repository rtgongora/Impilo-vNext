package zw.gov.mohcc.impilo.msika.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TariffView(
    String tariffId,
    String tariffCode,
    String name,
    String description,
    String category,
    BigDecimal unitPrice,
    String currency,
    String billingModel,
    LocalDate effectiveFrom,
    LocalDate effectiveTo,
    UUID tenantId,
    String status,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}
