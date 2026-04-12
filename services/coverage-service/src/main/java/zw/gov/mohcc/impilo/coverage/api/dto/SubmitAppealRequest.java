package zw.gov.mohcc.impilo.coverage.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;
import java.util.UUID;

public record SubmitAppealRequest(
        @NotNull UUID claimId,
        String appellantType,
        String appellantId,
        @NotBlank String reason,
        Map<String, Object> evidence,
        UUID coverageId
) {}
