package zw.gov.mohcc.impilo.inventory.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import zw.gov.mohcc.impilo.inventory.domain.ColdChainLocationType;

import java.util.UUID;

/**
 * Request to register a cold-chain location.
 */
public record RegisterColdChainLocationRequest(
        @NotNull UUID facilityId,
        UUID storeId,
        @NotBlank String name,
        ColdChainLocationType type,
        double tempMin,
        double tempMax
) {}
