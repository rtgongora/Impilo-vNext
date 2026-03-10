package zw.gov.mohcc.impilo.coverage.api.dto;

import java.time.LocalDate;
import java.util.UUID;

public record CoverageResponse(
        UUID id,
        String clientId,
        UUID planId,
        String planCode,
        String planName,
        String payerId,
        String memberNumber,
        String relationship,
        String status,
        LocalDate effectiveFrom,
        LocalDate effectiveTo
) {}
