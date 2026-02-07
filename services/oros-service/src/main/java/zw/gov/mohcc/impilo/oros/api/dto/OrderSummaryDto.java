package zw.gov.mohcc.impilo.oros.api.dto;

import zw.gov.mohcc.impilo.oros.domain.OrderPriority;
import zw.gov.mohcc.impilo.oros.domain.OrderStatus;
import zw.gov.mohcc.impilo.oros.domain.OrderType;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Summary DTO for order listings and API responses.
 * Uses a static factory method to map from the entity.
 */
public record OrderSummaryDto(
        String orderId,
        OrderType orderType,
        OrderStatus status,
        OrderPriority priority,
        String patientCpid,
        UUID facilityId,
        OffsetDateTime placedAt,
        String placedBy,
        String encounterRef,
        String ziboOrderCode,
        String clinicalNotes,
        OffsetDateTime updatedAt
) {
    /**
     * Factory method to create a summary DTO from an order entity.
     */
    public static OrderSummaryDto from(OrderEntity entity) {
        return new OrderSummaryDto(
                entity.getOrderId(),
                entity.getOrderType(),
                entity.getStatus(),
                entity.getPriority(),
                entity.getPatientCpid(),
                entity.getFacilityId(),
                entity.getPlacedAt(),
                entity.getPlacedBy(),
                entity.getEncounterRef(),
                entity.getZiboOrderCode(),
                entity.getClinicalNotes(),
                entity.getUpdatedAt()
        );
    }
}
