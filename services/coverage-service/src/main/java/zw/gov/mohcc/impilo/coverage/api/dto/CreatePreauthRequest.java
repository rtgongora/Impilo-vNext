package zw.gov.mohcc.impilo.coverage.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreatePreauthRequest(
        @NotNull UUID coverageId,
        @NotBlank String facilityId,
        @NotBlank String providerId,
        @NotBlank String requestType,
        String clinicalInfo,
        @NotBlank String requestedItems
) {}
