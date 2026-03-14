package zw.gov.mohcc.impilo.ops.mdc;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;

import java.io.IOException;

/**
 * Servlet filter that populates SLF4J MDC with v1.1 request context fields.
 *
 * <p>Fields injected into MDC:
 * <ul>
 *   <li>{@code tenant_id} — from X-Tenant-ID header</li>
 *   <li>{@code pod_id} — from X-Pod-ID header</li>
 *   <li>{@code request_id} — from X-Request-ID header</li>
 *   <li>{@code correlation_id} — from X-Correlation-ID header</li>
 *   <li>{@code user_id} — from X-Actor-ID header (if present)</li>
 * </ul>
 *
 * <p>Registered at filter order 5 (before V11HeaderFilter at order 10)
 * so that even rejected requests produce structured log lines.
 */
public class MdcFilter implements Filter {

    public static final String MDC_TENANT_ID = "tenant_id";
    public static final String MDC_POD_ID = "pod_id";
    public static final String MDC_REQUEST_ID = "request_id";
    public static final String MDC_CORRELATION_ID = "correlation_id";
    public static final String MDC_USER_ID = "user_id";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpReq = (HttpServletRequest) request;

        putIfPresent(MDC_TENANT_ID, httpReq.getHeader("X-Tenant-ID"));
        putIfPresent(MDC_POD_ID, httpReq.getHeader("X-Pod-ID"));
        putIfPresent(MDC_REQUEST_ID, httpReq.getHeader("X-Request-ID"));
        putIfPresent(MDC_CORRELATION_ID, httpReq.getHeader("X-Correlation-ID"));
        putIfPresent(MDC_USER_ID, httpReq.getHeader("X-Actor-ID"));

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_TENANT_ID);
            MDC.remove(MDC_POD_ID);
            MDC.remove(MDC_REQUEST_ID);
            MDC.remove(MDC_CORRELATION_ID);
            MDC.remove(MDC_USER_ID);
        }
    }

    private static void putIfPresent(String key, String value) {
        if (value != null && !value.isBlank()) {
            MDC.put(key, value);
        }
    }
}
