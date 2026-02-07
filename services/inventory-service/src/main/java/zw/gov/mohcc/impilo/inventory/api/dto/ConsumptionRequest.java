package zw.gov.mohcc.impilo.inventory.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Request DTO for posting a consumption event from a downstream clinical system.
 *
 * <p>Used by the internal consumption endpoints to record pharmacy, lab,
 * or generic clinical stock usage against the inventory ledger.</p>
 *
 * @param facilityId the facility where consumption occurred (required)
 * @param storeId    the store from which stock was consumed (required)
 * @param itemCode   the consumed item code (required)
 * @param batch      the batch number (optional for lab/clinical)
 * @param expiry     the expiry date (optional for lab/clinical)
 * @param qty        the quantity consumed (positive value, required)
 * @param orderId    the originating order identifier
 * @param refType    the reference type (e.g. OROS_ORDER, WARD, PROCEDURE)
 */
public record ConsumptionRequest(
        @NotNull UUID facilityId,
        @NotNull UUID storeId,
        @NotBlank String itemCode,
        String batch,
        LocalDate expiry,
        @Min(1) int qty,
        String orderId,
        String refType
) {}
