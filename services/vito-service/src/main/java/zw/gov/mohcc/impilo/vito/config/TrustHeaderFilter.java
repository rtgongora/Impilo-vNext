package zw.gov.mohcc.impilo.vito.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * Rejects requests missing mandatory TSHEPO trust headers.
 * Returns 400 with a standard JSON error response.
 *
 * Required headers (per TSHEPO enforcement contract):
 *   X-Tenant-Id, X-Correlation-Id, X-Actor-Id, X-Actor-Type
 */
@Component
@Order(2) // After TrustContextFilter (Order 1)
public class TrustHeaderFilter implements Filter {

    private static final List<String> REQUIRED_HEADERS = List.of(
            "X-Tenant-Id",
            "X-Correlation-Id",
            "X-Actor-Id",
            "X-Actor-Type"
    );

    private static final List<String> SKIP_PREFIXES = List.of(
            "/actuator/",
            "/v3/api-docs",
            "/swagger-ui"
    );

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpReq = (HttpServletRequest) request;
        HttpServletResponse httpRes = (HttpServletResponse) response;

        String path = httpReq.getRequestURI();

        // Skip health, docs, metrics
        for (String prefix : SKIP_PREFIXES) {
            if (path.startsWith(prefix)) {
                chain.doFilter(request, response);
                return;
            }
        }

        // Check mandatory headers
        for (String header : REQUIRED_HEADERS) {
            String value = httpReq.getHeader(header);
            if (value == null || value.isBlank()) {
                httpRes.setStatus(400);
                httpRes.setContentType("application/json");
                httpRes.getWriter().write(
                        "{\"error\":\"MISSING_TRUST_HEADER\",\"message\":\"Required header missing: " + header + "\",\"status\":400}");
                return;
            }
        }

        chain.doFilter(request, response);
    }
}
