package zw.gov.mohcc.impilo.notification.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeliveryReceiptRequest(

        @NotBlank
        @Size(max = 36)
        String notificationId,

        @NotBlank
        @Size(max = 32)
        String channel,

        @NotBlank
        @Size(max = 256)
        String recipientAddr,

        @NotBlank
        @Size(max = 32)
        String status,

        @Size(max = 256)
        String providerRef,

        String failureReason
) {
}
