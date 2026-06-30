package zw.gov.mohcc.impilo.inventory.api.dto;

import zw.gov.mohcc.impilo.inventory.domain.ReservationStatus;
import zw.gov.mohcc.impilo.inventory.persistence.entity.StockReservationEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * DTO for a Dura stock reservation.
 */
public record ReservationDto(
        UUID reservationId,
        UUID facilityId,
        UUID storeId,
        String itemCode,
        UUID batchLotId,
        int qty,
        String uom,
        ReservationStatus status,
        String refType,
        String refId,
        String reservedBy,
        String reason,
        OffsetDateTime expiresAt,
        OffsetDateTime createdAt
) {
    public static ReservationDto from(StockReservationEntity e) {
        return new ReservationDto(
                e.getReservationId(),
                e.getFacilityId(),
                e.getStoreId(),
                e.getItemCode(),
                e.getBatchLotId(),
                e.getQty(),
                e.getUom(),
                e.getStatus(),
                e.getRefType(),
                e.getRefId(),
                e.getReservedBy(),
                e.getReason(),
                e.getExpiresAt(),
                e.getCreatedAt()
        );
    }
}
