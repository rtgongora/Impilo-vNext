package zw.gov.mohcc.impilo.coverage.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record SubsidyProgramResponse(
        UUID id,
        String programCode,
        String programName,
        String payerId,
        String subsidyType,
        String status,
        BigDecimal annualCap,
        String currency,
        LocalDate effectiveFrom,
        LocalDate effectiveTo
) {}
