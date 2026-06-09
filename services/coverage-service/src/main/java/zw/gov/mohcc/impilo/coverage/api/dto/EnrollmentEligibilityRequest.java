package zw.gov.mohcc.impilo.coverage.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record EnrollmentEligibilityRequest(
        @NotBlank String clientId,
        @NotNull UUID planId
) {}
