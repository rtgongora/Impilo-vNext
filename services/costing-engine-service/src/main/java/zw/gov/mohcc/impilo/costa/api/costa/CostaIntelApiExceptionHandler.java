package zw.gov.mohcc.impilo.costa.api.costa;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.response.ApiError;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.UUID;

@RestControllerAdvice(basePackages = "zw.gov.mohcc.impilo.costa.api.costa")
public class CostaIntelApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> badRequest(IllegalArgumentException ex, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ApiResponse.error(
                new ApiError("BAD_REQUEST", ex.getMessage(), 400),
                correlationId(request)));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> conflict(IllegalStateException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(
                new ApiError("STATE", ex.getMessage(), 409),
                correlationId(request)));
    }

    private static String correlationId(HttpServletRequest request) {
        String h = request.getHeader(TrustContext.H_CORRELATION_ID);
        if (h != null && !h.isBlank()) {
            return h;
        }
        return UUID.randomUUID().toString();
    }
}
