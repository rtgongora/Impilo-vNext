package zw.gov.mohcc.impilo.tuso.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record WorkspaceOverrideRequest(
        @NotBlank(message = "Override type is required")
        String overrideType,

        @NotNull(message = "Reason is required for workspace overrides")
        @NotBlank(message = "Reason must not be blank")
        String reason,

        Map<String, Object> oldValue,

        Map<String, Object> newValue
) {}
