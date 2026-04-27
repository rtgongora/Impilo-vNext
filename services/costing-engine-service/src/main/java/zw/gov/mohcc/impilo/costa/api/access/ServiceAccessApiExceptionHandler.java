package zw.gov.mohcc.impilo.costa.api.access;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import zw.gov.mohcc.impilo.shared.response.ApiError;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.UUID;

@RestControllerAdvice(basePackages = "zw.gov.mohcc.impilo.costa.api.access")
public class ServiceAccessApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Object>> badRequest(IllegalArgumentException ex) {
        String cid = UUID.randomUUID().toString();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(
                        new ApiError("BAD_REQUEST", ex.getMessage(), 400),
                        cid));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> validation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .orElse("Validation failed");
        String cid = UUID.randomUUID().toString();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(new ApiError("VALIDATION_ERROR", msg, 400), cid));
    }
}
