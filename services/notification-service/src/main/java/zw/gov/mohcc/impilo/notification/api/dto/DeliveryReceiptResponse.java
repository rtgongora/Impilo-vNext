package zw.gov.mohcc.impilo.notification.api.dto;

import java.time.OffsetDateTime;

public record DeliveryReceiptResponse(
        String id,
        String notificationId,
        String channel,
        String recipientAddr,
        String status,
        String providerRef,
        OffsetDateTime deliveredAt,
        String failureReason,
        OffsetDateTime createdAt
) {
}
