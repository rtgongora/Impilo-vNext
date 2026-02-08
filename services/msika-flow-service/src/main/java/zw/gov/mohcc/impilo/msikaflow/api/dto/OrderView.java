package zw.gov.mohcc.impilo.msikaflow.api.dto;

import zw.gov.mohcc.impilo.msikaflow.persistence.entity.OrderEntity;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.OrderLineEntity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record OrderView(
        String orderId,
        UUID tenantId,
        String actorId,
        String actorType,
        String patientCpid,
        String orderType,
        String status,
        UUID facilityId,
        UUID vendorId,
        BigDecimal amountTotal,
        String currency,
        String priceSnapshot,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<OrderLineView> lines
) {
    public static OrderView from(OrderEntity order, List<OrderLineEntity> lines) {
        return new OrderView(
                order.getOrderId(),
                order.getTenantId(),
                order.getActorId(),
                order.getActorType().name(),
                order.getPatientCpid(),
                order.getOrderType().name(),
                order.getStatus().name(),
                order.getFacilityId(),
                order.getVendorId(),
                order.getAmountTotal(),
                order.getCurrency(),
                order.getPriceSnapshot(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                lines != null ? lines.stream().map(OrderLineView::from).toList() : List.of()
        );
    }
}
