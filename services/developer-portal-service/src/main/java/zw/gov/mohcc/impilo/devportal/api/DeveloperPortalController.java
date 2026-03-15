package zw.gov.mohcc.impilo.devportal.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.companion.error.ErrorEnvelope;
import zw.gov.mohcc.impilo.devportal.api.dto.IssueKeyRequest;
import zw.gov.mohcc.impilo.devportal.api.dto.RegisterClientRequest;
import zw.gov.mohcc.impilo.devportal.api.dto.RotateKeyRequest;
import zw.gov.mohcc.impilo.devportal.api.dto.RunCertificationRequest;
import zw.gov.mohcc.impilo.devportal.core.DeveloperPortalService;
import zw.gov.mohcc.impilo.devportal.domain.CertificationEntity;
import zw.gov.mohcc.impilo.devportal.domain.ClientEntity;

import java.util.*;

@RestController
public class DeveloperPortalController {

    private final DeveloperPortalService portalService;
    private final ObjectMapper objectMapper;

    public DeveloperPortalController(DeveloperPortalService portalService, ObjectMapper objectMapper) {
        this.portalService = portalService;
        this.objectMapper = objectMapper;
    }

    // ── Client Registration ──

    @PostMapping("/internal/v1/developer/clients")
    public ResponseEntity<?> registerClient(@Valid @RequestBody RegisterClientRequest request,
                                             jakarta.servlet.http.HttpServletRequest httpRequest) {
        RequestContext ctx = RequestContextHolder.require();
        String idempotencyKey = httpRequest.getHeader(CompanionHeaders.IDEMPOTENCY_KEY);
        Map<String, Object> result = portalService.registerClient(
                UUID.fromString(ctx.tenantId()), ctx.correlationId(), idempotencyKey, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/internal/v1/developer/clients")
    public ResponseEntity<?> listClients() {
        RequestContext ctx = RequestContextHolder.require();
        List<ClientEntity> clients = portalService.listClients(UUID.fromString(ctx.tenantId()));
        List<Map<String, Object>> items = clients.stream().map(this::toClientResponse).toList();
        return ResponseEntity.ok(Map.of("items", items, "count", items.size()));
    }

    @GetMapping("/internal/v1/developer/clients/{client_id}")
    public ResponseEntity<?> getClient(@PathVariable("client_id") UUID clientId) {
        RequestContext ctx = RequestContextHolder.require();
        return portalService.getClient(clientId)
                .map(c -> ResponseEntity.ok((Object) toClientResponse(c)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ErrorEnvelope.of("NOT_FOUND", "Client not found",
                                ctx.requestId(), ctx.correlationId())));
    }

    // ── API Key Management ──

    @PostMapping("/internal/v1/developer/clients/{client_id}/keys")
    public ResponseEntity<?> issueKey(@PathVariable("client_id") UUID clientId,
                                       @Valid @RequestBody IssueKeyRequest request,
                                       jakarta.servlet.http.HttpServletRequest httpRequest) {
        RequestContext ctx = RequestContextHolder.require();
        String idempotencyKey = httpRequest.getHeader(CompanionHeaders.IDEMPOTENCY_KEY);
        Map<String, Object> result = portalService.issueApiKey(
                clientId, UUID.fromString(ctx.tenantId()), ctx.correlationId(), idempotencyKey, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/internal/v1/developer/clients/{client_id}/keys")
    public ResponseEntity<?> listKeys(@PathVariable("client_id") UUID clientId) {
        RequestContextHolder.require();
        var keys = portalService.listApiKeys(clientId);
        List<Map<String, Object>> items = keys.stream().map(k -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key_id", k.getId());
            m.put("key_prefix", k.getKeyPrefix());
            m.put("label", k.getLabel());
            m.put("status", k.getStatus());
            m.put("created_at", k.getCreatedAt().toString());
            return m;
        }).toList();
        return ResponseEntity.ok(Map.of("items", items, "count", items.size()));
    }

    @PostMapping("/internal/v1/developer/keys/{key_id}/rotate")
    public ResponseEntity<?> rotateKey(@PathVariable("key_id") UUID keyId,
                                        @Valid @RequestBody RotateKeyRequest request,
                                        jakarta.servlet.http.HttpServletRequest httpRequest) {
        RequestContext ctx = RequestContextHolder.require();
        String idempotencyKey = httpRequest.getHeader(CompanionHeaders.IDEMPOTENCY_KEY);
        Map<String, Object> result = portalService.rotateApiKey(
                keyId, UUID.fromString(ctx.tenantId()), ctx.correlationId(), idempotencyKey, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    // ── Sandbox Configuration ──

    @PutMapping("/internal/v1/developer/clients/{client_id}/sandbox")
    public ResponseEntity<?> configureSandbox(@PathVariable("client_id") UUID clientId,
                                                @RequestBody Map<String, Object> request) {
        RequestContext ctx = RequestContextHolder.require();
        String sandboxConfig;
        try {
            sandboxConfig = objectMapper.writeValueAsString(request.get("config"));
        } catch (Exception e) {
            sandboxConfig = "{}";
        }
        Map<String, Object> result = portalService.configureSandbox(
                clientId, UUID.fromString(ctx.tenantId()), ctx.correlationId(), sandboxConfig);
        return ResponseEntity.ok(result);
    }

    // ── Deprecation Posture ──

    @PutMapping("/internal/v1/developer/clients/{client_id}/deprecation-posture")
    public ResponseEntity<?> updateDeprecationPosture(@PathVariable("client_id") UUID clientId,
                                                        @RequestBody Map<String, String> request) {
        RequestContextHolder.require();
        try {
            Map<String, Object> result = portalService.updateDeprecationPosture(clientId, request.get("posture"));
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── Key Revocation ──

    @DeleteMapping("/internal/v1/developer/keys/{key_id}")
    public ResponseEntity<?> revokeKey(@PathVariable("key_id") UUID keyId) {
        RequestContextHolder.require();
        try {
            Map<String, Object> result = portalService.revokeApiKey(keyId);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // ── Certification ──

    @PostMapping("/internal/v1/developer/clients/{client_id}/certify")
    public ResponseEntity<?> runCertification(@PathVariable("client_id") UUID clientId,
                                                @Valid @RequestBody RunCertificationRequest request,
                                                jakarta.servlet.http.HttpServletRequest httpRequest) {
        RequestContext ctx = RequestContextHolder.require();
        String idempotencyKey = httpRequest.getHeader(CompanionHeaders.IDEMPOTENCY_KEY);
        Map<String, Object> result = portalService.runCertification(
                clientId, UUID.fromString(ctx.tenantId()), ctx.correlationId(), idempotencyKey,
                request.triggeredBy());
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/internal/v1/developer/clients/{client_id}/certifications")
    public ResponseEntity<?> listCertifications(@PathVariable("client_id") UUID clientId) {
        RequestContextHolder.require();
        List<CertificationEntity> certs = portalService.listCertifications(clientId);
        List<Map<String, Object>> items = certs.stream().map(this::toCertificationResponse).toList();
        return ResponseEntity.ok(Map.of("items", items, "count", items.size()));
    }

    @GetMapping("/internal/v1/developer/certifications/{cert_id}")
    public ResponseEntity<?> getCertification(@PathVariable("cert_id") UUID certId) {
        RequestContext ctx = RequestContextHolder.require();
        return portalService.getCertification(certId)
                .map(c -> {
                    Map<String, Object> r = toCertificationResponse(c);
                    r.put("report", c.getReportJson());
                    return ResponseEntity.ok((Object) r);
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ErrorEnvelope.of("NOT_FOUND", "Certification not found",
                                ctx.requestId(), ctx.correlationId())));
    }

    // ── Federation Readiness ──

    @GetMapping("/internal/v1/developer/clients/{client_id}/federation-readiness")
    public ResponseEntity<?> federationReadiness(@PathVariable("client_id") UUID clientId) {
        RequestContextHolder.require();
        try {
            Map<String, Object> result = portalService.checkFederationReadiness(clientId);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    // ── Dashboard ──

    @GetMapping("/internal/v1/developer/dashboard/stats")
    public ResponseEntity<?> dashboardStats() {
        RequestContext ctx = RequestContextHolder.require();
        Map<String, Object> stats = portalService.getDashboardStats(UUID.fromString(ctx.tenantId()));
        return ResponseEntity.ok(stats);
    }

    // ── Discovery ──

    @GetMapping("/internal/v1/developer/discovery")
    public ResponseEntity<?> discovery() {
        RequestContextHolder.require();
        return ResponseEntity.ok(Map.of(
                "service", "developer-portal-service",
                "version", "1.0.0",
                "endpoints", List.of(
                        Map.of("method", "POST", "path", "/internal/v1/developer/clients", "description", "Register a partner client"),
                        Map.of("method", "GET", "path", "/internal/v1/developer/clients", "description", "List registered clients"),
                        Map.of("method", "GET", "path", "/internal/v1/developer/clients/{id}", "description", "Get client details"),
                        Map.of("method", "POST", "path", "/internal/v1/developer/clients/{id}/keys", "description", "Issue API key"),
                        Map.of("method", "GET", "path", "/internal/v1/developer/clients/{id}/keys", "description", "List client API keys"),
                        Map.of("method", "POST", "path", "/internal/v1/developer/keys/{id}/rotate", "description", "Rotate API key"),
                        Map.of("method", "DELETE", "path", "/internal/v1/developer/keys/{id}", "description", "Revoke API key"),
                        Map.of("method", "PUT", "path", "/internal/v1/developer/clients/{id}/sandbox", "description", "Configure sandbox"),
                        Map.of("method", "PUT", "path", "/internal/v1/developer/clients/{id}/deprecation-posture", "description", "Set deprecation posture"),
                        Map.of("method", "POST", "path", "/internal/v1/developer/clients/{id}/certify", "description", "Run certification checks"),
                        Map.of("method", "GET", "path", "/internal/v1/developer/clients/{id}/certifications", "description", "List certification history"),
                        Map.of("method", "GET", "path", "/internal/v1/developer/certifications/{id}", "description", "Get certification result"),
                        Map.of("method", "GET", "path", "/internal/v1/developer/clients/{id}/federation-readiness", "description", "Check federation readiness"),
                        Map.of("method", "GET", "path", "/internal/v1/developer/dashboard/stats", "description", "Dashboard statistics"),
                        Map.of("method", "GET", "path", "/internal/v1/developer/discovery", "description", "API discovery metadata")
                )
        ));
    }

    private Map<String, Object> toCertificationResponse(CertificationEntity c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("certification_id", c.getId());
        m.put("client_id", c.getClientId());
        m.put("status", c.getStatus());
        m.put("result", c.getResult());
        m.put("checks_total", c.getChecksTotal());
        m.put("checks_passed", c.getChecksPassed());
        m.put("checks_failed", c.getChecksFailed());
        m.put("triggered_by", c.getTriggeredBy());
        m.put("started_at", c.getStartedAt() != null ? c.getStartedAt().toString() : null);
        m.put("completed_at", c.getCompletedAt() != null ? c.getCompletedAt().toString() : null);
        return m;
    }

    private Map<String, Object> toClientResponse(ClientEntity c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("client_id", c.getId());
        m.put("client_name", c.getClientName());
        m.put("description", c.getDescription());
        m.put("contact_email", c.getContactEmail());
        m.put("status", c.getStatus());
        m.put("sandbox_enabled", c.isSandboxEnabled());
        m.put("deprecation_posture", c.getDeprecationPosture());
        m.put("created_at", c.getCreatedAt().toString());
        return m;
    }
}
