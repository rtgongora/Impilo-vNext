package zw.gov.mohcc.impilo.tshepo.offline.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request to generate a new offline pack for a facility.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OfflinePackRequest(
        @NotNull UUID tenantId,
        @NotNull UUID facilityId,
        @NotBlank String generatedFor
) {}
