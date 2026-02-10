package zw.gov.mohcc.impilo.vito.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * V1.1 header enforcement filter.
 *
 * Applies ONLY to /internal/v1/** and /external/v1/** paths.
 * Enforces mandatory v1.1 tenant context headers:
 *   X-Tenant-ID, X-Pod-ID, X-Request-ID, X-Correlation-ID
 *
 * If any are missing => HTTP 400 with standard v1.1 error envelope:
 *   error.code = "MISSING_REQUIRED_HEADER"
 *   details = { "missing": ["X-Tenant-ID", ...] }
 *
 * Does NOT interfere with:
 *   /actuator/**, /swagger/**, /v3/api-docs/**, /v1/** (legacy)
 *
 * Pure Servlet Filter — no Spring MVC dependency.
 */
@Component
@Order(10)
public class V1_1HeaderFilter implements Filter {

    private static final List<String> REQUIRED_HEADERS = List.of(
            "X-Tenant-ID",
            "X-Pod-ID",
            "X-Request-ID",
            "X-Correlation-ID"
    );

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpReq = (HttpServletRequest) request;
        HttpServletResponse httpRes = (HttpServletResponse) response;

        String path = httpReq.getRequestURI();

        // Only enforce on v1.1 paths
        if (!isV11Path(path)) {
            chain.doFilter(request, response);
            return;
        }

        // Check mandatory headers
        List<String> missing = new ArrayList<>();
        for (String header : REQUIRED_HEADERS) {
            String value = httpReq.getHeader(header);
            if (value == null || value.isBlank()) {
                missing.add(header);
            }
        }

        if (!missing.isEmpty()) {
            String requestId = httpReq.getHeader("X-Request-ID");
            String correlationId = httpReq.getHeader("X-Correlation-ID");

            V1_1ErrorWriter.writeError(httpRes, 400,
                    "MISSING_REQUIRED_HEADER",
                    "Required v1.1 headers missing: " + missing,
                    Map.of("missing", missing),
                    requestId, correlationId);
            return;
        }

        chain.doFilter(request, response);
    }

    static boolean isV11Path(String path) {
        return path != null && (path.startsWith("/internal/v1/") || path.startsWith("/external/v1/"));
    }
}
