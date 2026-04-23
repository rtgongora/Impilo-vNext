package zw.gov.mohcc.impilo.experience.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.shell.ShellWorkspaceAuthorizationService;
import zw.gov.mohcc.impilo.experience.shell.ShellWorkspaceStateService;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Redis-backed shell pins and recents (per tenant + actor), filtered using Tshepo visibility headers.
 */
@RestController
@RequestMapping("/internal/v1/shell/workspace-state")
public class ShellWorkspaceStateController {

    private final ShellWorkspaceStateService stateService;
    private final ShellWorkspaceAuthorizationService authorizationService;

    public ShellWorkspaceStateController(
            ShellWorkspaceStateService stateService,
            ShellWorkspaceAuthorizationService authorizationService) {
        this.stateService = stateService;
        this.authorizationService = authorizationService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getState(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            HttpServletRequest request) {
        if (actorId == null || actorId.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "error", Map.of("code", "VALIDATION", "message", "X-Actor-ID header is required")));
        }
        authorizationService.assertShellWorkspaceAccess("GET");
        Map<String, Object> data = stateService.load(tenantId, actorId, request);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("data", data);
        body.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(body);
    }

    @PutMapping
    public ResponseEntity<Map<String, Object>> putState(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            HttpServletRequest request,
            @RequestBody Map<String, Object> body) {
        if (actorId == null || actorId.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "error", Map.of("code", "VALIDATION", "message", "X-Actor-ID header is required")));
        }
        authorizationService.assertShellWorkspaceAccess("PUT");
        stateService.save(tenantId, actorId, body, request);
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("data", stateService.load(tenantId, actorId, request));
        envelope.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(envelope);
    }

    @DeleteMapping
    public ResponseEntity<Map<String, Object>> deleteState(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId) {
        if (actorId == null || actorId.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "error", Map.of("code", "VALIDATION", "message", "X-Actor-ID header is required")));
        }
        authorizationService.assertShellWorkspaceAccess("DELETE");
        stateService.delete(tenantId, actorId);
        return ResponseEntity.ok(Map.of(
                "data", Map.of("deleted", true),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }
}
