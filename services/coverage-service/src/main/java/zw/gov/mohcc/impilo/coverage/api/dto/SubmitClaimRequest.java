package zw.gov.mohcc.impilo.coverage.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record SubmitClaimRequest(
        @NotNull UUID coverageId,
        UUID preauthId,
        @NotBlank String facilityId,
        @NotBlank String providerId,
        String encounterId,
        @NotBlank String claimType,
        @NotBlank String lineItems,
        @NotNull BigDecimal totalAmount
) {}
