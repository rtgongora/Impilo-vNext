package zw.gov.mohcc.impilo.inventory.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Request DTO for fulfilling an approved requisition with batch-level details.
 *
 * @param lines the per-line fulfillment data
 */
public record FulfillRequisitionRequest(
        @NotEmpty @Valid List<FulfillLine> lines
) {

    /**
     * Per-line fulfillment data with batch and expiry details.
     *
     * @param lineId       the line identifier to fulfill
     * @param qtyFulfilled the fulfilled quantity
     * @param batch        the batch number of the fulfilled stock
     * @param expiry       the expiry date of the fulfilled stock
     */
    public record FulfillLine(
            UUID lineId,
            int qtyFulfilled,
            String batch,
            LocalDate expiry
    ) {}
}
