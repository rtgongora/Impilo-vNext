package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.VarapiServiceClient;
import zw.gov.mohcc.impilo.experience.providerregistry.ProviderRegistryAuditHelper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Registrar-facing certificate lifecycle for a provider (practising cert, registration cert,
 * good-standing letter). Surfaces the VARAPI CertificateController, which was previously
 * unreachable from the experience layer (R2/G7). Resolves the caller-held providerPublicId to
 * the numeric registry key (W0 bridge) and audits every mutation.
 */
@RestController
@RequestMapping("/internal/v1/registry/provider-certificates")
public class ProviderCertificateBffController {

    private static final Logger log = LoggerFactory.getLogger(ProviderCertificateBffController.class);
    private static final Set<String> ACTIONS = Set.of("issue", "renew", "suspend", "reinstate", "revoke");

    private final VarapiServiceClient varapiClient;
    private final ProviderRegistryAuditHelper audit;

    public ProviderCertificateBffController(VarapiServiceClient varapiClient, ProviderRegistryAuditHelper audit) {
        this.varapiClient = varapiClient;
        this.audit = audit;
    }

    @GetMapping("/provider/{providerPublicId}")
    public ResponseEntity<Map<String, Object>> listForProvider(
            @PathVariable String providerPublicId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            long providerId = resolveNumericId(providerPublicId);
            return ok(varapiClient.getCertificatesByProvider(providerId), requestId, correlationId);
        } catch (Exception e) {
            log.warn("Certificates listForProvider failed for {}: {}", providerPublicId, e.getMessage());
            return degraded(requestId, correlationId, e.getMessage());
        }
    }

    @GetMapping("/expiring")
    public ResponseEntity<Map<String, Object>> expiring(
            @RequestParam(defaultValue = "60") int daysAhead,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            return ok(varapiClient.getExpiringCertificates(daysAhead), requestId, correlationId);
        } catch (Exception e) {
            log.warn("Certificates expiring failed: {}", e.getMessage());
            return degraded(requestId, correlationId, e.getMessage());
        }
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purpose) {
        try {
            JsonNode created = varapiClient.createCertificate(body);
            String certId = created != null ? created.path("certificateId").asText(created.path("id").asText("")) : "";
            audit.emit("REGISTRY_CERTIFICATE_ISSUED", actorId, tenantId, correlationId, purpose,
                    "PROVIDER_CERTIFICATE", certId, "SUCCESS", Map.of("providerId", String.valueOf(body.get("providerId"))));
            return ResponseEntity.status(HttpStatus.CREATED).body(dataMeta(created, requestId, correlationId));
        } catch (Exception e) {
            return error(requestId, correlationId, "CERTIFICATE_ISSUE_FAILED", e.getMessage());
        }
    }

    @PostMapping("/{certificateId}/{action}")
    public ResponseEntity<Map<String, Object>> act(
            @PathVariable long certificateId,
            @PathVariable String action,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purpose) {
        String normalized = action == null ? "" : action.toLowerCase();
        if (!ACTIONS.contains(normalized)) {
            return error(requestId, correlationId, "UNSUPPORTED_ACTION", "Unsupported certificate action: " + action);
        }
        // suspend/revoke are sensitive — require a reason so the audit trail is meaningful.
        if (("suspend".equals(normalized) || "revoke".equals(normalized))
                && (body == null || String.valueOf(body.getOrDefault("reason", "")).isBlank())) {
            return error(requestId, correlationId, "REASON_REQUIRED", "A reason is required to " + normalized + " a certificate.");
        }
        try {
            JsonNode result = varapiClient.certificateAction(certificateId, normalized, body != null ? body : Map.of());
            audit.emit("REGISTRY_CERTIFICATE_" + normalized.toUpperCase(), actorId, tenantId, correlationId, purpose,
                    "PROVIDER_CERTIFICATE", String.valueOf(certificateId), "SUCCESS",
                    body != null ? body : Map.of());
            return ok(result, requestId, correlationId);
        } catch (Exception e) {
            return error(requestId, correlationId, "CERTIFICATE_" + normalized.toUpperCase() + "_FAILED", e.getMessage());
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private long resolveNumericId(String providerPublicId) {
        JsonNode provider = varapiClient.getProvider(providerPublicId);
        if (provider == null || !provider.hasNonNull("providerId")) {
            throw new IllegalStateException("Could not resolve numeric id for provider " + providerPublicId);
        }
        return provider.get("providerId").asLong();
    }

    private static ResponseEntity<Map<String, Object>> ok(JsonNode data, String requestId, String correlationId) {
        return ResponseEntity.ok(dataMeta(data, requestId, correlationId));
    }

    private static Map<String, Object> dataMeta(JsonNode data, String requestId, String correlationId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("data", data != null ? data : Map.of());
        m.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return m;
    }

    private static ResponseEntity<Map<String, Object>> degraded(String requestId, String correlationId, String message) {
        return ResponseEntity.ok(Map.of(
                "data", java.util.List.of(),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId,
                        "degraded", true, "source", "varapi-service", "reason", message != null ? message : "unavailable")));
    }

    private static ResponseEntity<Map<String, Object>> error(String requestId, String correlationId, String code, String message) {
        HttpStatus status = "REASON_REQUIRED".equals(code) || "UNSUPPORTED_ACTION".equals(code)
                ? HttpStatus.BAD_REQUEST : HttpStatus.BAD_GATEWAY;
        return ResponseEntity.status(status).body(Map.of(
                "error", Map.of("code", code, "message", message != null ? message : code),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }
}
