package zw.gov.mohcc.impilo.oros.api.dto;

import zw.gov.mohcc.impilo.oros.persistence.entity.OrderVersionEntity;

import java.time.OffsetDateTime;

/** Immutable order-version snapshot view (OF-B1). */
public record OrderVersionDto(
        String orderVersionId,
        String orderId,
        int version,
        String supersedesVersionId,
        String contentHash,
        String reason,
        String createdBy,
        OffsetDateTime createdAt
) {
    public static OrderVersionDto from(OrderVersionEntity e) {
        return new OrderVersionDto(
                e.getOrderVersionId(), e.getOrderId(), e.getVersion(),
                e.getSupersedesVersionId(), e.getContentHash(), e.getReason(),
                e.getCreatedBy(), e.getCreatedAt());
    }
}
