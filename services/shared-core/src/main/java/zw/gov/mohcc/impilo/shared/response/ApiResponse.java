package zw.gov.mohcc.impilo.shared.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Standardized JSON response envelope for all Impilo services.
 *
 * Every API response (success or error) from any of the 9 sovereign services
 * MUST use this format to ensure consistent client-side handling.
 *
 * Success: { "success": true, "data": {...}, "correlationId": "...", "timestamp": "..." }
 * Error:   { "success": false, "error": {...}, "correlationId": "...", "timestamp": "..." }
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        T data,
        ApiError error,
        String correlationId,
        Instant timestamp
) {
    public static <T> ApiResponse<T> ok(T data, String correlationId) {
        return new ApiResponse<>(true, data, null, correlationId, Instant.now());
    }

    public static <T> ApiResponse<T> error(ApiError error, String correlationId) {
        return new ApiResponse<>(false, null, error, correlationId, Instant.now());
    }

    public static <T> ApiResponse<T> error(String code, String message, int status, String correlationId) {
        return new ApiResponse<>(false, null, new ApiError(code, message, status), correlationId, Instant.now());
    }
}
