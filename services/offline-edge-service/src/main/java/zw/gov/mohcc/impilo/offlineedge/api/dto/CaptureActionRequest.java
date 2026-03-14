package zw.gov.mohcc.impilo.offlineedge.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.UUID;

public record CaptureActionRequest(
        @NotNull UUID entitlementId,
        @NotBlank String patientRef,
        @NotBlank String actionType,
        @NotNull Map<String, Object> payload,
        @NotBlank String capturedAt,
        String deviceId,
        int sequenceNum) {}
