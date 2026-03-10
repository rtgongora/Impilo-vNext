package zw.gov.mohcc.impilo.coverage.api.dto;

import java.time.LocalDate;
import java.util.UUID;

public record CoveragePlanResponse(
        UUID id,
        String planCode,
        String planName,
        String payerId,
        String planType,
        String status,
        LocalDate effectiveFrom,
        LocalDate effectiveTo
) {}
