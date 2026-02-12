package zw.gov.mohcc.impilo.companion.mock;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.companion.federation.FederationAuthority;

import java.util.Map;
import java.util.UUID;

/**
 * Mock controller exposing v1.1 endpoints for contract test verification.
 *
 * Endpoints:
 * - GET  /internal/v1/health — simple health check
 * - POST /internal/v1/test-command — command endpoint (idempotency enforced)
 * - POST /internal/v1/test-federation — national-only endpoint (federation enforced)
 */
@RestController
public class MockController {

    @GetMapping("/internal/v1/health")
    public ResponseEntity<Map<String, Object>> health() {
        RequestContext ctx = RequestContextHolder.require();
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "tenant_id", ctx.tenantId(),
                "pod_id", ctx.podId(),
                "request_id", ctx.requestId(),
                "correlation_id", ctx.correlationId()
        ));
    }

    @PostMapping("/internal/v1/test-command")
    public ResponseEntity<Map<String, Object>> testCommand(@RequestBody Map<String, Object> body) {
        RequestContext ctx = RequestContextHolder.require();
        String resourceId = UUID.randomUUID().toString();
        return ResponseEntity.status(201).body(Map.of(
                "id", resourceId,
                "name", body.getOrDefault("name", "unnamed"),
                "tenant_id", ctx.tenantId(),
                "request_id", ctx.requestId()
        ));
    }

    @PostMapping("/internal/v1/test-federation")
    public ResponseEntity<Map<String, Object>> testFederation(@RequestBody Map<String, Object> body) {
        // This endpoint is national-only
        FederationAuthority.requireNational();

        RequestContext ctx = RequestContextHolder.require();
        return ResponseEntity.ok(Map.of(
                "result", "federation-operation-completed",
                "pod_id", ctx.podId(),
                "action", body.getOrDefault("action", "unknown")
        ));
    }
}
