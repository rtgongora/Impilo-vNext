package zw.gov.mohcc.impilo.inventory.api.dto;

import zw.gov.mohcc.impilo.inventory.persistence.entity.SupplierCatalogueItemEntity;

import java.math.BigDecimal;
import java.util.UUID;

/** DTO for a supplier catalogue item. */
public record SupplierCatalogueItemDto(
        UUID catalogueItemId,
        UUID supplierId,
        String itemCode,
        String itemName,
        String packSize,
        BigDecimal unitPrice,
        String currency,
        int availableQty,
        boolean active
) {
    public static SupplierCatalogueItemDto from(SupplierCatalogueItemEntity e) {
        return new SupplierCatalogueItemDto(
                e.getCatalogueItemId(), e.getSupplierId(), e.getItemCode(), e.getItemName(),
                e.getPackSize(), e.getUnitPrice(), e.getCurrency(), e.getAvailableQty(), e.isActive());
    }
}
