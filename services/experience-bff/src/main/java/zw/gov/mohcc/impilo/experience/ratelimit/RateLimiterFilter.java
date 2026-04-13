package zw.gov.mohcc.impilo.experience.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * Per-tenant rate limiter backed by Redis sliding window.
 *
 * <p>Prevents a single tenant (or misbehaving client) from overwhelming
 * the BFF and its downstream sovereign services.</p>
 *
 * <p>Limits: 1000 requests per second per tenant.</p>
 *
 * <p>If Redis is unavailable, requests pass through (fail-open for
 * availability). Rate limiting is a best-effort protection, not a
 * hard security boundary.</p>
 */
@Component
public class RateLimiterFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterFilter.class);
    private static final long MAX_RPS_PER_TENANT = 1000;
    private static final String RATE_ERROR = "{\"error\":{\"code\":\"RATE_LIMITED\",\"message\":\"Too many requests. Please slow down.\"}}";

    private final StringRedisTemplate redisTemplate;

    public RateLimiterFilter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        // Skip rate limiting for health probes
        String path = request.getRequestURI();
        if (path.startsWith("/actuator") || path.startsWith("/internal/v1/health")) {
            chain.doFilter(request, response);
            return;
        }

        String tenantId = request.getHeader("X-Tenant-ID");
        if (tenantId == null || tenantId.isBlank()) {
            chain.doFilter(request, response);
            return;
        }

        try {
            String key = "rate:" + tenantId + ":" + (System.currentTimeMillis() / 1000);
            Long count = redisTemplate.opsForValue().increment(key);

            if (count != null && count == 1) {
                redisTemplate.expire(key, Duration.ofSeconds(2));
            }

            if (count != null && count > MAX_RPS_PER_TENANT) {
                log.warn("Rate limit exceeded for tenant={} count={}", tenantId, count);
                response.setStatus(429);
                response.setContentType("application/json");
                response.getWriter().write(RATE_ERROR);
                return;
            }
        } catch (Exception e) {
            // Redis unavailable — fail-open (let request through)
            log.trace("Rate limiter Redis unavailable — passing through");
        }

        chain.doFilter(request, response);
    }
}
