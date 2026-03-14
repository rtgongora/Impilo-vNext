package zw.gov.mohcc.impilo.integration.connectors;

import java.util.Map;

public record ConnectorRequest(
        String routeId,
        String method,
        String path,
        Map<String, String> headers,
        String body,
        String targetUrl,
        Integer timeoutMs
) {
}
