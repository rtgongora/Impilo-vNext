package zw.gov.mohcc.impilo.mushex.api.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record SettlementRunRequest(
        @NotNull LocalDate periodStart,
        @NotNull LocalDate periodEnd
) {}
