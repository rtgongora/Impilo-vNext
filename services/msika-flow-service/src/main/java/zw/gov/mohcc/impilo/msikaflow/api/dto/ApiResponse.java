package zw.gov.mohcc.impilo.msikaflow.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

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

    public static <T> ApiResponse<T> error(String code, String message, int status, String correlationId) {
        return new ApiResponse<>(false, null, new ApiError(code, message, status), correlationId, Instant.now());
    }

    public record ApiError(String code, String message, int status) {}
}
