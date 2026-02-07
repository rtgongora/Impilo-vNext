package zw.gov.mohcc.impilo.pharmacy.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Individual pick item data within a {@link PickRequest}.
 *
 * @param itemId      the dispense item ID
 * @param batchNumber the batch number of the picked stock
 * @param expiryDate  the expiry date of the batch
 * @param barcode     the scanned barcode (GTIN-14, GTIN-13, or internal)
 * @param qtyPicked   the quantity picked from the shelf
 */
public record PickItemData(
        @NotNull UUID itemId,
        String batchNumber,
        LocalDate expiryDate,
        String barcode,
        @Min(1) int qtyPicked
) {}
