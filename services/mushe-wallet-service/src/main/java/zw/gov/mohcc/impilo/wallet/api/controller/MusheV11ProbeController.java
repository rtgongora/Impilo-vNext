package zw.gov.mohcc.impilo.wallet.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Standard v1.1 health and test-command probe for the Mushe Wallet service.
 */
@RestController
@RequestMapping("/internal/v1")
public class MusheV11ProbeController {

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        var ctx = RequestContextHolder.get();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service", "mushe-wallet");
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
        body.put("service", "mushe-wallet");
        body.put("action", "test-command");
        if (ctx != null) {
            body.put("request_id", ctx.requestId());
            body.put("correlation_id", ctx.correlationId());
        }
        body.put("echo", payload);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }
}
