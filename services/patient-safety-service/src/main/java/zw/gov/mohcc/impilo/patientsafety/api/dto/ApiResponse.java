package zw.gov.mohcc.impilo.patientsafety.api.dto;

/** Standard API envelope for patient-safety-service internal endpoints. */
public record ApiResponse<T>(T data) {
    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(data);
    }
}
