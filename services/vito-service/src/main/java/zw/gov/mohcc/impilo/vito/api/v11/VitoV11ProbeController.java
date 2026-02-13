package zw.gov.mohcc.impilo.vito.api.v11;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.companion.federation.FederationAuthority;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal v1.1 probe controller for VITO.
 *
 * Exercises the companion enforcement pipeline:
 *   - GET  /internal/v1/health         — header enforcement proof
 *   - POST /internal/v1/test-command   — idempotency proof
 *   - POST /internal/v1/test-federation — federation authority proof
 *
 * Note: VITO also has the production V11PatientsController at
 * /internal/v1/patients/merge. These probe endpoints coexist
 * alongside it for contract verification.
 */
@RestController
@RequestMapping("/internal/v1")
public class VitoV11ProbeController {

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        var ctx = RequestContextHolder.require();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service", "vito");
        body.put("status", "UP");
        body.put("tenant_id", ctx.tenantId());
        body.put("pod_id", ctx.podId());
        body.put("request_id", ctx.requestId());
        body.put("correlation_id", ctx.correlationId());
        return ResponseEntity.ok(body);
    }

    @PostMapping("/test-command")
    public ResponseEntity<Map<String, Object>> testCommand(@RequestBody Map<String, Object> payload) {
        var ctx = RequestContextHolder.require();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service", "vito");
        body.put("action", "test-command");
        body.put("request_id", ctx.requestId());
        body.put("correlation_id", ctx.correlationId());
        body.put("echo", payload);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PostMapping("/test-federation")
    public ResponseEntity<Map<String, Object>> testFederation(@RequestBody Map<String, Object> payload) {
        var ctx = RequestContextHolder.require();
        FederationAuthority.requireNational(ctx.podId());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service", "vito");
        body.put("action", "test-federation");
        body.put("pod_id", ctx.podId());
        body.put("request_id", ctx.requestId());
        body.put("correlation_id", ctx.correlationId());
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }
}
