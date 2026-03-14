package zw.gov.mohcc.impilo.integration.connectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class HttpConnector implements Connector {

    private static final Logger log = LoggerFactory.getLogger(HttpConnector.class);
    private static final int DEFAULT_TIMEOUT_MS = 30_000;

    private final HttpClient httpClient;

    public HttpConnector() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public ConnectorType type() {
        return ConnectorType.HTTP;
    }

    @Override
    public ConnectorResult execute(ConnectorRequest request) {
        try {
            int timeoutMs = request.timeoutMs() != null ? request.timeoutMs() : DEFAULT_TIMEOUT_MS;

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(request.targetUrl()))
                    .timeout(Duration.ofMillis(timeoutMs));

            // Set headers
            if (request.headers() != null) {
                request.headers().forEach(builder::header);
            }

            // Set method and body
            HttpRequest.BodyPublisher bodyPublisher = request.body() != null
                    ? HttpRequest.BodyPublishers.ofString(request.body())
                    : HttpRequest.BodyPublishers.noBody();

            builder.method(request.method() != null ? request.method() : "POST", bodyPublisher);

            HttpRequest httpRequest = builder.build();

            log.info("HttpConnector dispatching {} {} (route={})",
                    request.method(), request.targetUrl(), request.routeId());

            HttpResponse<String> response = httpClient.send(httpRequest,
                    HttpResponse.BodyHandlers.ofString());

            int statusCode = response.statusCode();
            String responseBody = response.body();

            if (statusCode >= 200 && statusCode < 300) {
                log.info("HttpConnector success: route={}, status={}", request.routeId(), statusCode);
                return ConnectorResult.success(statusCode, responseBody);
            } else {
                log.warn("HttpConnector non-success: route={}, status={}", request.routeId(), statusCode);
                return ConnectorResult.failure(statusCode, responseBody);
            }
        } catch (Exception e) {
            log.error("HttpConnector error: route={}, error={}", request.routeId(), e.getMessage(), e);
            return ConnectorResult.error("HTTP dispatch failed: " + e.getMessage());
        }
    }
}
