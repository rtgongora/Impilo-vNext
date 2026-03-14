package zw.gov.mohcc.impilo.ops.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Servlet filter that records golden-signal latency and error metrics.
 *
 * <p>Metrics recorded:
 * <ul>
 *   <li>{@code impilo.ops.http.latency} — histogram timer by endpoint class (internal/external), method, status</li>
 *   <li>{@code impilo.ops.http.errors} — counter incremented for 4xx/5xx responses, tagged by error code</li>
 * </ul>
 *
 * <p>Registered at filter order 6 (after MDC, before v1.1 header enforcement).
 */
public class GoldenSignalsFilter implements Filter {

    private final MeterRegistry registry;

    public GoldenSignalsFilter(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpReq = (HttpServletRequest) request;
        HttpServletResponse httpRes = (HttpServletResponse) response;

        long start = System.nanoTime();

        try {
            chain.doFilter(request, response);
        } finally {
            long durationNanos = System.nanoTime() - start;
            String path = httpReq.getRequestURI();
            String endpointClass = classifyEndpoint(path);
            String method = httpReq.getMethod();
            int status = httpRes.getStatus();

            // Latency histogram
            Timer.builder("impilo.ops.http.latency")
                    .description("HTTP request latency by endpoint class")
                    .tags(List.of(
                            Tag.of("endpoint_class", endpointClass),
                            Tag.of("method", method),
                            Tag.of("status", String.valueOf(status))
                    ))
                    .register(registry)
                    .record(durationNanos, TimeUnit.NANOSECONDS);

            // Error counter for 4xx/5xx
            if (status >= 400) {
                String errorCode = extractErrorCode(httpRes);
                registry.counter("impilo.ops.http.errors",
                        "endpoint_class", endpointClass,
                        "method", method,
                        "status", String.valueOf(status),
                        "error_code", errorCode
                ).increment();
            }
        }
    }

    private static String classifyEndpoint(String path) {
        if (path == null) return "unknown";
        if (path.startsWith("/internal/")) return "internal";
        if (path.startsWith("/external/")) return "external";
        if (path.startsWith("/actuator/")) return "actuator";
        return "other";
    }

    private static String extractErrorCode(HttpServletResponse response) {
        String errorCode = response.getHeader("X-Error-Code");
        return errorCode != null ? errorCode : "HTTP_" + response.getStatus();
    }
}
