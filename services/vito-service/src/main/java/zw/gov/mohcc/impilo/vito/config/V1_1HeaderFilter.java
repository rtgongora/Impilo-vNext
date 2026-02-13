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
 * V1.1 header enforcement filter (RETIRED — superseded by Tech Companion library).
 *
 * The companion library's {@code V11HeaderFilter} is now authoritative for
 * /internal/v1/** and /external/v1/** paths, registered via CompanionV11Config.
 *
 * This class is retained for reference but is no longer auto-registered
 * (the @Component annotation has been removed).
 */
// @Component — RETIRED: companion library V11HeaderFilter is authoritative
// @Order(10)
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
