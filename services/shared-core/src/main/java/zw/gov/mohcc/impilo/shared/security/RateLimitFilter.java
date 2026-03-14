package zw.gov.mohcc.impilo.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import zw.gov.mohcc.impilo.security.ratelimit.RateLimitGuard;
import zw.gov.mohcc.impilo.security.ratelimit.RateLimitResult;
import zw.gov.mohcc.impilo.shared.response.ApiError;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.io.IOException;
import java.util.Objects;

/**
 * Servlet filter that enforces service-level rate limiting using
 * {@link RateLimitGuard}. Returns 429 with the standard {@link ApiResponse}
 * error envelope when the limit is exceeded.
 * <p>
 * Bucket key defaults to the actor ID from trust headers, falling back to
 * remote address. Register as a bean in each service's SecurityConfig.
 * <p>
 * Rate-limit headers are always set on responses:
 * <ul>
 *   <li>X-RateLimit-Limit — bucket capacity</li>
 *   <li>X-RateLimit-Remaining — tokens remaining</li>
 *   <li>Retry-After — seconds until tokens are available (only on 429)</li>
 * </ul>
 */
public class RateLimitFilter extends OncePerRequestFilter {

    public static final String RATE_LIMITED = "RATE_LIMITED";

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private final RateLimitGuard guard;

    public RateLimitFilter(RateLimitGuard guard) {
        this.guard = Objects.requireNonNull(guard);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String key = resolveKey(request);
        RateLimitResult result = guard.tryAcquire(key);

        // Always set rate-limit headers
        response.setHeader(RateLimitResult.HEADER_LIMIT, String.valueOf(result.limit()));
        response.setHeader(RateLimitResult.HEADER_REMAINING, String.valueOf(result.remainingTokens()));

        if (!result.allowed()) {
            response.setHeader(RateLimitResult.HEADER_RETRY_AFTER, String.valueOf(result.retryAfterSeconds()));
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

            String correlationId = request.getHeader("x-correlation-id");
            ApiResponse<?> errorResponse = ApiResponse.error(
                    new ApiError(RATE_LIMITED, "Too many requests. Retry after " + result.retryAfterSeconds() + " seconds.", 429),
                    correlationId != null ? correlationId : "unknown"
            );
            MAPPER.writeValue(response.getWriter(), errorResponse);
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Health and readiness probes should not be rate-limited.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator/") || path.equals("/health") || path.equals("/ready");
    }

    private String resolveKey(HttpServletRequest request) {
        String actorId = request.getHeader("x-actor-id");
        if (actorId != null && !actorId.isBlank()) {
            return actorId;
        }
        return request.getRemoteAddr();
    }
}
