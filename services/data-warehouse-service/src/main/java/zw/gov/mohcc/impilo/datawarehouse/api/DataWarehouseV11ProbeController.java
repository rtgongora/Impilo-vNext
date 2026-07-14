package zw.gov.mohcc.impilo.datawarehouse.api;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.companion.federation.FederationAuthority;

/**
 * v1.1 probe controller for data-warehouse-service.
 *
 * Exercises the tech-companion enforcement pipeline:
 *   - POST /internal/v1/test-command    -- idempotency proof
 *   - POST /internal/v1/test-federation -- federation authority proof
 */
@RestController
@RequestMapping("/internal/v1")
public class DataWarehouseV11ProbeController {


    @PostMapping("/test-command")
    public ResponseEntity<Map<String, Object>> testCommand(@RequestBody Map<String, Object> payload) {
        var ctx = RequestContextHolder.require();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service", "data-warehouse-service");
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
        body.put("service", "data-warehouse-service");
        body.put("action", "test-federation");
        body.put("pod_id", ctx.podId());
        body.put("request_id", ctx.requestId());
        body.put("correlation_id", ctx.correlationId());
        return ResponseEntity.ok(body);
    }
}
