package zw.gov.mohcc.impilo.notification.api.dto;

public record NotifyResponse(
        String id, String status, String channel, String to, String detail) {
    public static NotifyResponse of(String id, String status, String channel, String to) {
        return new NotifyResponse(id, status, channel, to, null);
    }
}
