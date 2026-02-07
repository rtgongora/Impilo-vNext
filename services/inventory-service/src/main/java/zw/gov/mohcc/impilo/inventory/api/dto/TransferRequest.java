package zw.gov.mohcc.impilo.inventory.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Request DTO for posting an inter-store stock transfer.
 *
 * <p>Creates paired TRANSFER_OUT and TRANSFER_IN ledger events:
 * a negative delta on the source store and a positive delta on the
 * destination store.</p>
 *
 * @param sourceFacilityId the source facility identifier
 * @param sourceStoreId    the source store identifier
 * @param destFacilityId   the destination facility identifier
 * @param destStoreId      the destination store identifier
 * @param itemCode         the item code
 * @param batch            the batch number
 * @param expiry           the expiry date
 * @param qty              the transfer quantity (positive value)
 * @param uom              the unit of measure
 * @param reason           the reason for the transfer
 * @param idempotencyKey   unique key for deduplication
 */
public record TransferRequest(
        @NotNull UUID sourceFacilityId,
        @NotNull UUID sourceStoreId,
        @NotNull UUID destFacilityId,
        @NotNull UUID destStoreId,
        @NotBlank String itemCode,
        String batch,
        LocalDate expiry,
        @Min(1) int qty,
        @NotBlank String uom,
        String reason,
        @NotBlank String idempotencyKey
) {}
