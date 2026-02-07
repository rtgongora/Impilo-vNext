package zw.gov.mohcc.impilo.inventory.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

/**
 * Request DTO for approving a requisition with per-line approved quantities.
 *
 * @param lines the per-line approval decisions
 */
public record ApproveRequisitionRequest(
        @NotEmpty @Valid List<ApprovalLine> lines
) {

    /**
     * Per-line approval decision.
     *
     * @param lineId      the line identifier to approve
     * @param qtyApproved the approved quantity
     */
    public record ApprovalLine(
            UUID lineId,
            int qtyApproved
    ) {}
}
