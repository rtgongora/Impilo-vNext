package zw.gov.mohcc.impilo.coverage.api.dto;

import java.time.LocalDate;
import java.util.UUID;

public record SubsidyEnrollmentResponse(
        UUID id,
        String clientId,
        UUID subsidyProgramId,
        String programCode,
        String exemptionCategory,
        String status,
        LocalDate effectiveFrom,
        LocalDate effectiveTo
) {}
