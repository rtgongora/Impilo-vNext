package zw.gov.mohcc.impilo.coverage.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateMemberCoverageRequest(
        @NotBlank String clientId,
        @NotNull UUID planId,
        @NotBlank String memberNumber,
        String relationship,
        String status
) {}
