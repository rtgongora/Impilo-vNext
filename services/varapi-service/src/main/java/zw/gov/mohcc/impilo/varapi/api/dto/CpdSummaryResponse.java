package zw.gov.mohcc.impilo.varapi.api.dto;

import java.time.LocalDate;
import java.util.List;

public record CpdSummaryResponse(
        String cycleName,
        LocalDate startDate,
        LocalDate endDate,
        int requiredPoints,
        int earnedPoints,
        String status,
        List<CpdEventDto> events
) {}
