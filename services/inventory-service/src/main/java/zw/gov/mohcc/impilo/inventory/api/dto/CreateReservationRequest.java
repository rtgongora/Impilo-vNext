package zw.gov.mohcc.impilo.inventory.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Request to reserve stock for a workflow.
 */
public record CreateReservationRequest(
        @NotNull UUID facilityId,
        @NotNull UUID storeId,
        @NotBlank String itemCode,
        UUID batchLotId,
        @Positive int qty,
        String uom,
        String refType,
        String refId,
        String reason,
        OffsetDateTime expiresAt
) {}
