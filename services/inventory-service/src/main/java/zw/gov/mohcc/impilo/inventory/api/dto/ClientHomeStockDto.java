package zw.gov.mohcc.impilo.inventory.api.dto;

import zw.gov.mohcc.impilo.inventory.domain.HomeStockSource;
import zw.gov.mohcc.impilo.inventory.domain.HomeStockStatus;
import zw.gov.mohcc.impilo.inventory.persistence.entity.ClientHomeStockEntity;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * DTO for a client household stock item.
 */
public record ClientHomeStockDto(
        UUID homeStockId,
        String clientId,
        String itemCode,
        String itemName,
        int qty,
        String uom,
        String batchNumber,
        LocalDate expiryDate,
        HomeStockSource source,
        String managedByCaregiver,
        HomeStockStatus status,
        String notes,
        OffsetDateTime updatedAt
) {
    public static ClientHomeStockDto from(ClientHomeStockEntity e) {
        return new ClientHomeStockDto(
                e.getHomeStockId(), e.getClientId(), e.getItemCode(), e.getItemName(), e.getQty(),
                e.getUom(), e.getBatchNumber(), e.getExpiryDate(), e.getSource(),
                e.getManagedByCaregiver(), e.getStatus(), e.getNotes(), e.getUpdatedAt());
    }
}
