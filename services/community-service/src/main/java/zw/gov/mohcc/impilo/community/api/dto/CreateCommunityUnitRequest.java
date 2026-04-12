package zw.gov.mohcc.impilo.community.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateCommunityUnitRequest(
        @NotNull UUID tenantId,
        @NotBlank String name,
        @NotBlank String unitType,
        UUID facilityId,
        String province,
        String district,
        String ward,
        Integer catchmentPopulation
) {}
