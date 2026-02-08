package zw.gov.mohcc.impilo.msikaflow.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

public record SubstitutionProposeRequest(
        @NotBlank String lineId,
        @NotBlank String substituteCode,
        BigDecimal substitutePrice,
        String reason
) {}
