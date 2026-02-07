package zw.gov.mohcc.impilo.tshepo.identity.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request to generate an offline provisional CPID (O-CPID).
 */
public record ProvisionalCpidRequest(
        @NotNull UUID tenantId,
        @NotNull UUID facilityId,
        @NotBlank String deviceFingerprint
) {}
