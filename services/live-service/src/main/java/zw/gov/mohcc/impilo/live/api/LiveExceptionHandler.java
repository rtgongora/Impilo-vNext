package zw.gov.mohcc.impilo.live.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import zw.gov.mohcc.impilo.live.core.LiveGovernanceException;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps Impilo Live governance violations to structured HTTP error responses so that owning services
 * receive a precise, auditable reason when doctrine blocks an operation.
 */
@RestControllerAdvice
public class LiveExceptionHandler {

    @ExceptionHandler(LiveGovernanceException.class)
    public ResponseEntity<Map<String, Object>> handleGovernance(LiveGovernanceException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "live_governance_violation");
        body.put("code", ex.getCode());
        body.put("message", ex.getMessage());
        body.put("status", ex.getStatus().value());
        body.put("timestamp", OffsetDateTime.now().toString());
        return ResponseEntity.status(ex.getStatus()).body(body);
    }
}
