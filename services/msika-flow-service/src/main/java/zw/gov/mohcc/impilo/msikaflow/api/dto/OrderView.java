package zw.gov.mohcc.impilo.msikaflow.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
        JsonNode metadata,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<OrderLineView> lines
) {
    /** Stateless parse of stored metadata JSON so consumers (BFF) receive an object, not a string. */
    private static final ObjectMapper MAPPER = new ObjectMapper();

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
                parseMetadata(order.getMetadata()),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                lines != null ? lines.stream().map(OrderLineView::from).toList() : List.of()
        );
    }

    private static JsonNode parseMetadata(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }
}
