package zw.gov.mohcc.impilo.tuso.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record StartShiftRequest(
        @NotNull(message = "Workspace ID is required")
        UUID workspaceId,

        @NotBlank(message = "Provider ID is required")
        String providerId,

        @NotBlank(message = "Provider type is required")
        String providerType
) {}
