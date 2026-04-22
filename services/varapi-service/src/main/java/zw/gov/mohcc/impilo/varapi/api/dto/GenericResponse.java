package zw.gov.mohcc.impilo.varapi.api.dto;

import java.util.Map;

public record GenericResponse(
        String status,
        String message,
        Object data
) {
    public static GenericResponse success(String message) {
        return new GenericResponse("success", message, null);
    }

    public static GenericResponse success(String message, Map<String, Object> data) {
        return new GenericResponse("success", message, data);
    }

    public static GenericResponse error(String message) {
        return new GenericResponse("error", message, null);
    }
}
