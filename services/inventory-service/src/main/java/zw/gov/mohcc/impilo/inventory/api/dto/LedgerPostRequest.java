package zw.gov.mohcc.impilo.inventory.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Request DTO for posting a single ledger event (receipt, issue, adjustment, wastage, return).
 *
 * @param facilityId     the facility identifier (required)
 * @param storeId        the store identifier (required)
 * @param binId          the bin identifier (optional)
 * @param itemCode       the item code (required)
 * @param batch          the batch number
 * @param expiry         the expiry date
 * @param qty            the quantity (positive value; sign is determined by event type)
 * @param uom            the unit of measure (required)
 * @param reason         the reason for the event
 * @param refType        the reference type (e.g. PO, TRANSFER, COUNT)
 * @param refId          the reference identifier
 * @param idempotencyKey unique key for deduplication (required)
 */
public record LedgerPostRequest(
        @NotNull UUID facilityId,
        @NotNull UUID storeId,
        UUID binId,
        @NotBlank String itemCode,
        String batch,
        LocalDate expiry,
        @Min(1) int qty,
        @NotBlank String uom,
        String reason,
        String refType,
        String refId,
        @NotBlank String idempotencyKey
) {}
