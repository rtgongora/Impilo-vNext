package zw.gov.mohcc.impilo.ops.otel;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;

import java.io.IOException;

/**
 * OpenTelemetry trace context propagation filter.
 *
 * <p>Extracts W3C Trace Context headers ({@code traceparent}, {@code tracestate})
 * and propagates trace/span IDs into SLF4J MDC for structured log correlation.
 *
 * <p>This is a lightweight hook that works even without a full OTel SDK.
 * When the OTel Java agent or SDK is attached, it handles the actual span
 * creation. This filter ensures the trace context is available in logs
 * regardless of SDK presence.
 *
 * <p>Registered at filter order 4 (before MDC filter at 5).
 */
public class OtelPropagationFilter implements Filter {

    public static final String MDC_TRACE_ID = "trace_id";
    public static final String MDC_SPAN_ID = "span_id";
    public static final String TRACEPARENT_HEADER = "traceparent";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpReq = (HttpServletRequest) request;
        HttpServletResponse httpRes = (HttpServletResponse) response;

        String traceparent = httpReq.getHeader(TRACEPARENT_HEADER);
        if (traceparent != null && !traceparent.isBlank()) {
            TraceContext ctx = parseTraceparent(traceparent);
            if (ctx != null) {
                MDC.put(MDC_TRACE_ID, ctx.traceId());
                MDC.put(MDC_SPAN_ID, ctx.spanId());
            }
        }

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_TRACE_ID);
            MDC.remove(MDC_SPAN_ID);
        }
    }

    /**
     * Parses W3C traceparent header: {@code version-traceid-spanid-flags}
     * Example: {@code 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01}
     */
    public static TraceContext parseTraceparent(String traceparent) {
        if (traceparent == null) return null;
        String[] parts = traceparent.split("-");
        if (parts.length < 4) return null;
        return new TraceContext(parts[1], parts[2]);
    }

    public record TraceContext(String traceId, String spanId) {}
}
