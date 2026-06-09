package zw.gov.mohcc.impilo.coverage.api.dto;

import java.util.UUID;

public record EnrollmentEligibilityResponse(
        String resultCode,
        String resultMessage,
        UUID planId,
        String planCode,
        String clientId,
        boolean eligible
) {}
