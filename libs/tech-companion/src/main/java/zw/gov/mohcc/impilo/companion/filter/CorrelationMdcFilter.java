package zw.gov.mohcc.impilo.companion.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;

import java.io.IOException;

/**
 * Injects v1.1 correlation headers into SLF4J MDC for structured logging.
 *
 * <p>MDC keys populated:
 * <ul>
 *   <li>{@code tenant_id} — from {@code X-Tenant-ID}</li>
 *   <li>{@code pod_id} — from {@code X-Pod-ID}</li>
 *   <li>{@code request_id} — from {@code X-Request-ID}</li>
 *   <li>{@code correlation_id} — from {@code X-Correlation-ID}</li>
 *   <li>{@code actor_id} — from JWT principal name (if available)</li>
 * </ul>
 *
 * <p>Runs at order 15 (after V11HeaderFilter at 10, IdempotencyFilter at 11,
 * TimeoutFilter at 12). MDC is always cleared in finally block.
 *
 * <p>Applies to ALL paths (not just v1.1) so that actuator and health endpoints
 * also benefit from correlation if headers are present.
 */
public class CorrelationMdcFilter implements Filter {

    public static final String MDC_TENANT_ID      = "tenant_id";
    public static final String MDC_POD_ID          = "pod_id";
    public static final String MDC_REQUEST_ID      = "request_id";
    public static final String MDC_CORRELATION_ID  = "correlation_id";
    public static final String MDC_ACTOR_ID        = "actor_id";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpReq = (HttpServletRequest) request;

        putIfPresent(MDC_TENANT_ID, httpReq.getHeader(CompanionHeaders.TENANT_ID));
        putIfPresent(MDC_POD_ID, httpReq.getHeader(CompanionHeaders.POD_ID));
        putIfPresent(MDC_REQUEST_ID, httpReq.getHeader(CompanionHeaders.REQUEST_ID));
        putIfPresent(MDC_CORRELATION_ID, httpReq.getHeader(CompanionHeaders.CORRELATION_ID));

        // Extract actor from principal if available
        if (httpReq.getUserPrincipal() != null) {
            putIfPresent(MDC_ACTOR_ID, httpReq.getUserPrincipal().getName());
        }

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_TENANT_ID);
            MDC.remove(MDC_POD_ID);
            MDC.remove(MDC_REQUEST_ID);
            MDC.remove(MDC_CORRELATION_ID);
            MDC.remove(MDC_ACTOR_ID);
        }
    }

    private void putIfPresent(String key, String value) {
        if (value != null && !value.isBlank()) {
            MDC.put(key, value);
        }
    }
}
