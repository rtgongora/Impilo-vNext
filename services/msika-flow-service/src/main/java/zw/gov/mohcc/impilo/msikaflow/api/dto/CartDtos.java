package zw.gov.mohcc.impilo.msikaflow.api.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class CartDtos {
    private CartDtos() {}

    public record AddCartItemRequest(
            String msikaCoreCode,
            /** msika-service listing id — the pricing source of truth resolved at checkout. */
            String listingId,
            String kind,
            int qty,
            String fulfillmentMode,
            Object metadata
    ) {}

    public record CartItemView(
            String id,
            String msikaCoreCode,
            String kind,
            int qty,
            String fulfillmentMode,
            Object metadata,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            String listingId,
            java.math.BigDecimal unitPrice,
            String currency
    ) {}

    public record CartView(
            String cartId,
            UUID tenantId,
            String actorId,
            String actorType,
            String patientCpid,
            String channel,
            String status,
            Object metadata,
            List<CartItemView> items,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {}

    public record CheckoutRequest(
            String orderType,
            UUID facilityId,
            UUID vendorId,
            String idempotencyKey
    ) {}

    public record CheckoutResponse(
            String cartId,
            String orderId,
            String status
    ) {}
}

