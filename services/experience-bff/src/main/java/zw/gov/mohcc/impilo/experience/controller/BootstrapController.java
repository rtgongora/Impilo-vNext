package zw.gov.mohcc.impilo.experience.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.bootstrap.BootstrapDtos;
import zw.gov.mohcc.impilo.experience.bootstrap.BootstrapService;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/bootstrap")
public class BootstrapController {

    private final BootstrapService bootstrapService;

    public BootstrapController(BootstrapService bootstrapService) {
        this.bootstrapService = bootstrapService;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status(
            @RequestHeader(value = CompanionHeaders.TENANT_ID, required = false) String tenantId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        return ok(requestId, correlationId, bootstrapService.status(tenantId));
    }

    @PostMapping("/validate-token")
    public ResponseEntity<Map<String, Object>> validateToken(
            @RequestHeader(value = CompanionHeaders.TENANT_ID, required = false) String tenantId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId,
            @RequestBody BootstrapDtos.ValidateTokenRequest body) {
        return ok(requestId, correlationId, bootstrapService.validateToken(tenantId, body));
    }

    @PostMapping("/first-admin")
    public ResponseEntity<Map<String, Object>> firstAdmin(
            @RequestHeader(value = CompanionHeaders.TENANT_ID, required = false) String tenantId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId,
            @RequestBody BootstrapDtos.BootstrapFirstAdminRequest body) {
        return ok(requestId, correlationId, bootstrapService.createFirstAdmin(tenantId, body));
    }

    @PostMapping("/activate")
    public ResponseEntity<Map<String, Object>> activate(
            @RequestHeader(value = CompanionHeaders.TENANT_ID, required = false) String tenantId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId,
            @RequestBody BootstrapDtos.BootstrapActivateRequest body) {
        return ok(requestId, correlationId, bootstrapService.activate(tenantId, body));
    }

    @PostMapping("/close")
    public ResponseEntity<Map<String, Object>> close(
            @RequestHeader(value = CompanionHeaders.TENANT_ID, required = false) String tenantId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        return ok(requestId, correlationId, bootstrapService.close(tenantId));
    }

    @PostMapping("/recovery/open")
    public ResponseEntity<Map<String, Object>> openRecovery(
            @RequestHeader(value = CompanionHeaders.TENANT_ID, required = false) String tenantId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId,
            @RequestBody BootstrapDtos.BootstrapRecoveryRequest body) {
        return ok(requestId, correlationId, bootstrapService.openRecovery(tenantId, body));
    }

    @PostMapping("/recovery/close")
    public ResponseEntity<Map<String, Object>> closeRecovery(
            @RequestHeader(value = CompanionHeaders.TENANT_ID, required = false) String tenantId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId,
            @RequestBody(required = false) BootstrapDtos.BootstrapRecoveryRequest body) {
        return ok(requestId, correlationId, bootstrapService.closeRecovery(tenantId, body != null ? body : new BootstrapDtos.BootstrapRecoveryRequest(null, null)));
    }

    private static ResponseEntity<Map<String, Object>> ok(String requestId, String correlationId, Object attributes) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of("type", "bootstrap-action", "attributes", attributes));
        response.put("meta", Map.of(
                "request_id", requestId != null ? requestId : UUID.randomUUID().toString(),
                "correlation_id", correlationId != null ? correlationId : UUID.randomUUID().toString()
        ));
        return ResponseEntity.ok(response);
    }
}
