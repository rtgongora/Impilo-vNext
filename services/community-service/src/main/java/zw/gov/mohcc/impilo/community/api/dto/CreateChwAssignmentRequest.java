package zw.gov.mohcc.impilo.community.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateChwAssignmentRequest(
        @NotNull UUID tenantId,
        @NotNull UUID unitId,
        @NotBlank String providerId,
        String role
) {}
