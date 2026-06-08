package zw.gov.mohcc.impilo.live.media;

public record MediaHealthStatus(
        String roomId,
        String status,
        boolean healthy,
        String provider,
        String message
) {
}
