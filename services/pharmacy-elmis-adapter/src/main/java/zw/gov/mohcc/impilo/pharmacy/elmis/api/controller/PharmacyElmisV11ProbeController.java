package zw.gov.mohcc.impilo.pharmacy.elmis.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal v1.1 probe controller for Pharmacy eLMIS Adapter.
 *
 * Exercises the tech-companion enforcement pipeline:
 *   - GET  /internal/v1/health         — header enforcement proof
 *   - POST /internal/v1/test-command   — idempotency proof
 */
@RestController
@RequestMapping("/internal/v1")
public class PharmacyElmisV11ProbeController {

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        var ctx = RequestContextHolder.require();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service", "pharmacy-elmis-adapter");
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
        body.put("service", "pharmacy-elmis-adapter");
        body.put("action", "test-command");
        body.put("request_id", ctx.requestId());
        body.put("correlation_id", ctx.correlationId());
        body.put("echo", payload);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }
}
