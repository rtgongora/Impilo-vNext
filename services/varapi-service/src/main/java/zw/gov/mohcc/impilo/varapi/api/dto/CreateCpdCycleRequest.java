package zw.gov.mohcc.impilo.varapi.api.dto;

import java.time.LocalDate;

public record CreateCpdCycleRequest(
        Long councilId,
        String cycleName,
        LocalDate startDate,
        LocalDate endDate,
        int requiredPoints
) {}
