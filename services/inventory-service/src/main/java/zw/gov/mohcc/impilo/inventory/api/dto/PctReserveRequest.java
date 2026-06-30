package zw.gov.mohcc.impilo.inventory.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

/**
 * PCT request to reserve stock for a clinical encounter / prescription.
 */
public record PctReserveRequest(
        @NotNull UUID facilityId,
        @NotNull UUID storeId,
        @NotBlank String itemCode,
        UUID batchLotId,
        @Positive int qty,
        String uom,
        @NotBlank String encounterId,
        String refType,
        String reason
) {
    /** Reference type, defaulting to ENCOUNTER when unspecified. */
    public String resolvedRefType() {
        return (refType == null || refType.isBlank()) ? "ENCOUNTER" : refType;
    }
}
