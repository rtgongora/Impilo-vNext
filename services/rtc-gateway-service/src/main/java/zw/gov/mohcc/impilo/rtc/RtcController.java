package zw.gov.mohcc.impilo.rtc;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.rtc.model.RtcParticipantTokenRequest;
import zw.gov.mohcc.impilo.rtc.model.RtcSessionProvisionRequest;
import zw.gov.mohcc.impilo.rtc.model.RtcSessionResponse;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/internal/v1/rtc")
public class RtcController {
    private final RtcGatewayService service;

    public RtcController(RtcGatewayService service) {
        this.service = service;
    }

    @PostMapping("/sessions")
    public ResponseEntity<Map<String, Object>> provision(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @Valid @RequestBody RtcSessionProvisionRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(envelope(service.provision(body), requestId, correlationId));
    }

    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<Map<String, Object>> get(
            @PathVariable String sessionId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return ResponseEntity.ok(envelope(service.get(sessionId), requestId, correlationId));
    }

    @PostMapping("/sessions/{sessionId}/participants/token")
    public ResponseEntity<Map<String, Object>> token(
            @PathVariable String sessionId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @Valid @RequestBody RtcParticipantTokenRequest body) {
        return ResponseEntity.ok(envelope(service.issueToken(sessionId, body), requestId, correlationId));
    }

    @GetMapping("/ops/health")
    public ResponseEntity<Map<String, Object>> opsHealth(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("data", service.opsHealth());
        out.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(out);
    }

    @PostMapping("/sessions/{sessionId}/end")
    public ResponseEntity<Map<String, Object>> end(
            @PathVariable String sessionId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody(required = false) Map<String, Object> ignored) {
        return ResponseEntity.ok(envelope(service.end(sessionId), requestId, correlationId));
    }

    @ExceptionHandler(RtcNotFoundException.class)
    public ResponseEntity<Map<String, Object>> notFound(RtcNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error("RTC_SESSION_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> unavailable(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error("RTC_PROVIDER_UNAVAILABLE", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> invalidRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error("RTC_INVALID_REQUEST", ex.getMessage()));
    }

    private Map<String, Object> envelope(RtcSessionResponse data, String requestId, String correlationId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("data", data);
        out.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return out;
    }

    private Map<String, Object> error(String code, String message) {
        return Map.of("error", Map.of("code", code, "message", message == null ? code : message));
    }
}
