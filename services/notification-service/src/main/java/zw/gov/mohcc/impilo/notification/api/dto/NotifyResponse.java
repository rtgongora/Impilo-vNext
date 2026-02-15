package zw.gov.mohcc.impilo.notification.api.dto;

public record NotifyResponse(
        String id,
        String status,
        String channel,
        String to
) {
}
