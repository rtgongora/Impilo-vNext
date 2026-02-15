package zw.gov.mohcc.impilo.notification.api.dto;

public record WorkerResponse(
        int processed,
        int sent,
        int failed
) {
}
