package zw.gov.mohcc.impilo.coverage.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CheckEligibilityRequest(
        @NotNull UUID coverageId,
        @NotBlank String patientRef,
        String serviceCode
) {}
