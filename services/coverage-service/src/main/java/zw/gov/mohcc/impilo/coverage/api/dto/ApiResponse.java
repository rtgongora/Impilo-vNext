package zw.gov.mohcc.impilo.coverage.api.dto;

/**
 * Standard API envelope for coverage-service internal endpoints.
 */
public record ApiResponse<T>(T data) {

    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(data);
    }
}
