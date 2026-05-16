package zw.gov.mohcc.impilo.experience.controller.dto;

public final class ApiResponseFactory {

    private ApiResponseFactory() {
    }

    public static <T> ApiEnvelope<T> ok(T data, String requestId, String correlationId) {
        return new ApiEnvelope<>(data, new ApiMeta(requestId, correlationId));
    }

    public static ApiErrorEnvelope error(String code, String message, String requestId, String correlationId) {
        return new ApiErrorEnvelope(
                new ApiError(code, message),
                new ApiMeta(requestId, correlationId)
        );
    }
}
