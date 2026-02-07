package zw.gov.mohcc.impilo.tshepo.identity.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request to verify a MOSIP link reference. Verification checks whether the
 * supplied linkRef matches the stored encrypted reference for the given Health ID.
 */
public record MosipVerifyRequest(
        @NotNull UUID tenantId,
        @NotNull UUID healthId,
        @NotBlank String linkRef
) {}
