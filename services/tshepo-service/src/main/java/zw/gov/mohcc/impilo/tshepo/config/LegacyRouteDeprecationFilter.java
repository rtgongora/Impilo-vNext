package zw.gov.mohcc.impilo.tshepo.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

/**
 * Emits deprecation telemetry for legacy compatibility routes to support TSHEPO monolith retirement gates.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class LegacyRouteDeprecationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(LegacyRouteDeprecationFilter.class);
    private static final String LEGACY_PREFIX = "/v1/";

    private final LegacyRouteUsageTracker tracker;

    public LegacyRouteDeprecationFilter(LegacyRouteUsageTracker tracker) {
        this.tracker = tracker;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path != null && path.startsWith(LEGACY_PREFIX)) {
            String routeKey = request.getMethod() + " " + path;
            tracker.record(
                    routeKey,
                    headerOrNull(request, "X-Actor-Id"),
                    headerOrNull(request, "X-Caller-Service"),
                    headerOrNull(request, "X-Correlation-Id"),
                    Instant.now());
            response.addHeader("Deprecation", "true");
            response.addHeader("Sunset", "2026-12-31");
            log.warn("TSHEPO legacy compatibility route accessed: {}", routeKey);
        }
        filterChain.doFilter(request, response);
    }

    private static String headerOrNull(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        return value != null && !value.isBlank() ? value : null;
    }
}
