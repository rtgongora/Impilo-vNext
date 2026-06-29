package zw.gov.mohcc.impilo.inventory.api.dto;

import jakarta.validation.constraints.NotNull;
import zw.gov.mohcc.impilo.inventory.domain.BatchLotStatus;

/**
 * Request to transition a batch's governance status.
 */
public record SetBatchStatusRequest(
        @NotNull BatchLotStatus status,
        String reason
) {}
