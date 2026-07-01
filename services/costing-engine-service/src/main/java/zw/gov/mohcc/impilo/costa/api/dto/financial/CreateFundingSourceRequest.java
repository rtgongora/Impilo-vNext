package zw.gov.mohcc.impilo.costa.api.dto.financial;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateFundingSourceRequest(
        @NotBlank String name,
        @NotBlank String code,
        @NotBlank String sourceType,
        String donorName,
        String grantReference,
        BigDecimal ceilingAmount,
        LocalDate startDate,
        LocalDate endDate
) {}
