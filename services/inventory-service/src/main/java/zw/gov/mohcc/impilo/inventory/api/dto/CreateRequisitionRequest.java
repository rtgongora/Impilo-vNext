package zw.gov.mohcc.impilo.inventory.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Request DTO for creating a new inter-store requisition.
 *
 * @param fromStoreId the requesting (destination) store
 * @param toStoreId   the supplying (source) store
 * @param lines       the line items to request (must not be empty)
 */
public record CreateRequisitionRequest(
        @NotNull UUID fromStoreId,
        @NotNull UUID toStoreId,
        @NotEmpty @Valid List<ReqLineData> lines
) {

    /**
     * A single requisition line item.
     *
     * @param itemCode     the item code to request
     * @param qtyRequested the quantity requested
     * @param notes        optional line-level notes
     */
    public record ReqLineData(
            String itemCode,
            int qtyRequested,
            String notes
    ) {}
}
