package zw.gov.mohcc.impilo.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import zw.gov.mohcc.impilo.security.input.InputValidationException;
import zw.gov.mohcc.impilo.security.ratelimit.RateLimitExceededException;
import zw.gov.mohcc.impilo.security.ratelimit.RateLimitResult;
import zw.gov.mohcc.impilo.shared.response.ApiError;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

/**
 * Global exception handler for security-baseline exceptions.
 * Produces the standard {@link ApiResponse} error envelope.
 * Ordered to run before service-specific handlers.
 */
@ControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityBaselineExceptionHandler {

    @ExceptionHandler(InputValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleInputValidation(
            InputValidationException ex, HttpServletRequest request) {

        String correlationId = resolveCorrelationId(request);
        ApiResponse<Void> body = ApiResponse.error(
                new ApiError(
                        ApiError.VALIDATION_FAILED,
                        "Validation failed on field '" + ex.getFieldName()
                                + "': " + ex.getErrorCode(),
                        400
                ),
                correlationId
        );
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleRateLimit(
            RateLimitExceededException ex, HttpServletRequest request) {

        RateLimitResult result = ex.getResult();
        String correlationId = resolveCorrelationId(request);

        ApiResponse<Void> body = ApiResponse.error(
                new ApiError(
                        RateLimitFilter.RATE_LIMITED,
                        "Too many requests. Retry after " + result.retryAfterSeconds() + " seconds.",
                        429
                ),
                correlationId
        );

        return ResponseEntity.status(429)
                .header(RateLimitResult.HEADER_LIMIT, String.valueOf(result.limit()))
                .header(RateLimitResult.HEADER_REMAINING, String.valueOf(result.remainingTokens()))
                .header(RateLimitResult.HEADER_RETRY_AFTER, String.valueOf(result.retryAfterSeconds()))
                .body(body);
    }

    private String resolveCorrelationId(HttpServletRequest request) {
        String id = request.getHeader("x-correlation-id");
        return id != null ? id : "unknown";
    }
}
