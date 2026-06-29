package zw.gov.mohcc.impilo.inventory.api.dto;

import zw.gov.mohcc.impilo.inventory.domain.SupplierOrderStatus;
import zw.gov.mohcc.impilo.inventory.persistence.entity.SupplierOrderEntity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** DTO for a supplier order (header + optional lines). */
public record SupplierOrderDto(
        UUID supplierOrderId,
        UUID supplierId,
        UUID buyerFacilityId,
        SupplierOrderStatus status,
        BigDecimal totalAmount,
        String currency,
        String placedBy,
        String notes,
        OffsetDateTime createdAt,
        OffsetDateTime dispatchedAt,
        OffsetDateTime deliveredAt,
        List<SupplierOrderLineDto> lines
) {
    public static SupplierOrderDto from(SupplierOrderEntity e, List<SupplierOrderLineDto> lines) {
        return new SupplierOrderDto(
                e.getSupplierOrderId(), e.getSupplierId(), e.getBuyerFacilityId(), e.getStatus(),
                e.getTotalAmount(), e.getCurrency(), e.getPlacedBy(), e.getNotes(),
                e.getCreatedAt(), e.getDispatchedAt(), e.getDeliveredAt(), lines);
    }

    public static SupplierOrderDto from(SupplierOrderEntity e) {
        return from(e, null);
    }
}
