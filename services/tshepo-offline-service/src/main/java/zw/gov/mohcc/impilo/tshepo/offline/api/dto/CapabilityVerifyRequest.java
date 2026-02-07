package zw.gov.mohcc.impilo.tshepo.offline.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request to verify an offline capability token.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CapabilityVerifyRequest(
        @NotBlank String signedToken,
        @NotNull UUID tenantId
) {}
