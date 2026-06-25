package zw.gov.mohcc.impilo.khuluma.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * v1.1 probe endpoints — health + echo test-command — matching the convention used across
 * Impilo services for golden-contract verification.
 */
@RestController
@RequestMapping("/internal/v1")
public class KhulumaV11ProbeController {

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        var ctx = RequestContextHolder.get();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service", "khuluma");
        body.put("status", "UP");
        if (ctx != null) {
            body.put("tenant_id", ctx.tenantId());
            body.put("pod_id", ctx.podId());
            body.put("request_id", ctx.requestId());
            body.put("correlation_id", ctx.correlationId());
        }
        return ResponseEntity.ok(body);
    }

    @PostMapping("/test-command")
    public ResponseEntity<Map<String, Object>> testCommand(@RequestBody Map<String, Object> payload) {
        var ctx = RequestContextHolder.get();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service", "khuluma");
        body.put("action", "test-command");
        if (ctx != null) {
            body.put("request_id", ctx.requestId());
            body.put("correlation_id", ctx.correlationId());
        }
        body.put("echo", payload);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }
}
