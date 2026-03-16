package zw.gov.mohcc.impilo.experience.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Auth session endpoint for golden path A (Email Login) and B (Provider ID + Biometric).
 * In Stage-1, this provides a real JWT-shaped session response without requiring
 * full Keycloak integration. SPEC CONFLICT: TSHEPO/Keycloak integration details
 * are defined externally in the TSHEPO/Keycloak integration specification.
 */
@RestController
@RequestMapping("/internal/v1/auth")
public class AuthSessionController {

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody Map<String, Object> body) {

        String method = body.getOrDefault("method", "email").toString();
        String identifier = body.getOrDefault("identifier", "").toString();

        // Stage-1: return a session token without full OIDC flow
        String sessionToken = UUID.randomUUID().toString();
        OffsetDateTime expiresAt = OffsetDateTime.now().plusHours(8);

        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", UUID.randomUUID().toString());
        user.put("identifier", identifier);
        user.put("method", method);
        user.put("tenant_id", tenantId);
        user.put("roles", java.util.List.of("CLINICIAN"));

        Map<String, Object> session = new LinkedHashMap<>();
        session.put("token", sessionToken);
        session.put("expires_at", expiresAt.toString());
        session.put("user", user);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of(
                "id", sessionToken,
                "type", "Session",
                "attributes", session
        ));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey) {

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of("status", "logged_out"));
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.ok(response);
    }

    @GetMapping("/session")
    public ResponseEntity<Map<String, Object>> getSession(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {

        Map<String, Object> session = new LinkedHashMap<>();
        session.put("authenticated", authorization != null && !authorization.isBlank());
        session.put("tenant_id", tenantId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", session);
        response.put("meta", Map.of(
                "request_id", requestId,
                "correlation_id", correlationId
        ));

        return ResponseEntity.ok(response);
    }
}
