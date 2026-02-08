package zw.gov.mohcc.impilo.mushex.api.dto;

public record WebhookPayload(
        String adapterRef,
        String status,
        String message,
        String signature,
        String rawPayload
) {}
