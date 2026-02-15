package zw.gov.mohcc.impilo.integration.api.dto;

public record DispatchResponse(
        String id,
        String status,
        String routeId,
        String targetUrl
) {
}
