package zw.gov.mohcc.impilo.experience.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Request/response logging filter for production debugging.
 *
 * <p>Logs every request with: method, path, tenant, actor, correlation ID,
 * response status, and duration. Uses MDC for structured logging
 * compatible with ELK/Loki.</p>
 *
 * <p>Does NOT log request/response bodies (PHI risk).</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestResponseLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestResponseLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        // Skip actuator endpoints
        if (request.getRequestURI().startsWith("/actuator")) {
            chain.doFilter(request, response);
            return;
        }

        long start = System.currentTimeMillis();

        // Set MDC for structured logging
        String requestId = request.getHeader("X-Request-ID");
        String correlationId = request.getHeader("X-Correlation-ID");
        String tenantId = request.getHeader("X-Tenant-ID");
        String actorId = request.getHeader("X-Actor-ID");

        if (requestId != null) MDC.put("request_id", requestId);
        if (correlationId != null) MDC.put("correlation_id", correlationId);
        if (tenantId != null) MDC.put("tenant_id", tenantId);
        if (actorId != null) MDC.put("actor_id", actorId);

        try {
            chain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - start;
            int status = response.getStatus();

            if (status >= 500) {
                log.error("{} {} → {} ({}ms) tenant={} actor={} corr={}",
                        request.getMethod(), request.getRequestURI(), status, duration,
                        tenantId, actorId, correlationId);
            } else if (status >= 400) {
                log.warn("{} {} → {} ({}ms) tenant={} actor={}",
                        request.getMethod(), request.getRequestURI(), status, duration,
                        tenantId, actorId);
            } else if (duration > 1000) {
                log.warn("SLOW {} {} → {} ({}ms) tenant={} actor={}",
                        request.getMethod(), request.getRequestURI(), status, duration,
                        tenantId, actorId);
            } else {
                log.debug("{} {} → {} ({}ms)",
                        request.getMethod(), request.getRequestURI(), status, duration);
            }

            MDC.clear();
        }
    }
}
