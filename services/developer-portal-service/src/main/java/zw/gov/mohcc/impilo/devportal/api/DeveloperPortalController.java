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
import zw.gov.mohcc.impilo.devportal.core.DeveloperPortalService;
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

    // ── Discovery ──

    @GetMapping("/internal/v1/developer/discovery")
    public ResponseEntity<?> discovery() {
        RequestContextHolder.require();
        return ResponseEntity.ok(Map.of(
                "service", "developer-portal-service",
                "version", "1.0.0",
                "endpoints", List.of(
                        Map.of("method", "POST", "path", "/internal/v1/developer/clients", "description", "Register a client"),
                        Map.of("method", "GET", "path", "/internal/v1/developer/clients", "description", "List clients"),
                        Map.of("method", "POST", "path", "/internal/v1/developer/clients/{id}/keys", "description", "Issue API key"),
                        Map.of("method", "POST", "path", "/internal/v1/developer/keys/{id}/rotate", "description", "Rotate API key")
                )
        ));
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
