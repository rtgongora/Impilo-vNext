package zw.gov.mohcc.impilo.fhirgateway.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import zw.gov.mohcc.impilo.fhirgateway.interceptor.PiiViolationException;

import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class FhirGatewayExceptionHandler {

    @ExceptionHandler(PiiViolationException.class)
    public ResponseEntity<Map<String, Object>> handlePiiViolation(PiiViolationException ex) {
        return ResponseEntity.unprocessableEntity().body(Map.of(
                "resourceType", "OperationOutcome",
                "issue", List.of(Map.of(
                        "severity", "error",
                        "code", "processing",
                        "diagnostics", ex.getMessage(),
                        "details", Map.of(
                                "text", "PII violation detected — the BUTANO SHR is PII-free"
                        )
                ))
        ));
    }
}
