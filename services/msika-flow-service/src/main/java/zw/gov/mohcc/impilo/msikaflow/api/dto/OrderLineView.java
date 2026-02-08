package zw.gov.mohcc.impilo.msikaflow.api.dto;

import zw.gov.mohcc.impilo.msikaflow.persistence.entity.OrderLineEntity;

import java.math.BigDecimal;

public record OrderLineView(
        String lineId,
        String orderId,
        String msikaCoreCode,
        String kind,
        int qty,
        BigDecimal unitPrice,
        BigDecimal lineTotal,
        String fulfillmentMode,
        String state,
        String metadata
) {
    public static OrderLineView from(OrderLineEntity e) {
        return new OrderLineView(
                e.getLineId(), e.getOrderId(), e.getMsikaCoreCode(),
                e.getKind() != null ? e.getKind().name() : null,
                e.getQty(), e.getUnitPrice(), e.getLineTotal(),
                e.getFulfillmentMode() != null ? e.getFulfillmentMode().name() : null,
                e.getState() != null ? e.getState().name() : null,
                e.getMetadata()
        );
    }
}
