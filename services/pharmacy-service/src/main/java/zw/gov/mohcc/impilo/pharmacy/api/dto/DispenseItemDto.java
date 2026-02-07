package zw.gov.mohcc.impilo.pharmacy.api.dto;

import zw.gov.mohcc.impilo.pharmacy.domain.DispenseItemStatus;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.DispenseItemEntity;

import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO for a single dispense line item within an order.
 * Uses a static factory method to map from the entity.
 */
public record DispenseItemDto(
        UUID itemId,
        String drugCode,
        String drugDisplay,
        int qtyRequested,
        int qtyDispensed,
        int qtyRemaining,
        DispenseItemStatus status,
        String batchNumber,
        LocalDate expiryDate,
        String barcode,
        String substitution,
        String instructions,
        String pickedBy,
        String dispensedBy
) {
    /**
     * Factory method to create an item DTO from a dispense item entity.
     */
    public static DispenseItemDto from(DispenseItemEntity entity) {
        return new DispenseItemDto(
                entity.getItemId(),
                entity.getDrugCode(),
                entity.getDrugDisplay(),
                entity.getQtyRequested(),
                entity.getQtyDispensed(),
                entity.getQtyRemaining(),
                entity.getStatus(),
                entity.getBatchNumber(),
                entity.getExpiryDate(),
                entity.getBarcode(),
                entity.getSubstitution(),
                entity.getInstructions(),
                entity.getPickedBy(),
                entity.getDispensedBy()
        );
    }
}
