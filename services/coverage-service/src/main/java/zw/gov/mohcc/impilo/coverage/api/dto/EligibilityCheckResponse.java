package zw.gov.mohcc.impilo.coverage.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record EligibilityCheckResponse(
        UUID id,
        UUID coverageId,
        String patientRef,
        String serviceCode,
        String resultCode,
        String resultMessage,
        OffsetDateTime checkedAt
) {}
