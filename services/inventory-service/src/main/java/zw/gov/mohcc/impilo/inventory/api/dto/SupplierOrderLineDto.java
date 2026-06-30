package zw.gov.mohcc.impilo.inventory.api.dto;

import zw.gov.mohcc.impilo.inventory.persistence.entity.SupplierOrderLineEntity;

import java.math.BigDecimal;
import java.util.UUID;

/** DTO for a supplier order line. */
public record SupplierOrderLineDto(
        UUID lineId,
        String itemCode,
        String itemName,
        int qty,
        BigDecimal unitPrice,
        BigDecimal lineTotal
) {
    public static SupplierOrderLineDto from(SupplierOrderLineEntity e) {
        return new SupplierOrderLineDto(
                e.getLineId(), e.getItemCode(), e.getItemName(), e.getQty(), e.getUnitPrice(), e.getLineTotal());
    }
}
