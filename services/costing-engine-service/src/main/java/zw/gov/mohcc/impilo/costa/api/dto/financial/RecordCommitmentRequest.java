package zw.gov.mohcc.impilo.costa.api.dto.financial;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RecordCommitmentRequest(
        @NotNull UUID budgetLineId,
        @NotBlank String ownerSystem,
        @NotBlank String ownerReference,
        String description,
        @NotNull BigDecimal committedAmount,
        BigDecimal obligatedAmount,
        OffsetDateTime expiresAt
) {}
