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
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Registrar/operator credentials wiring for a provider (R2/G11): qualifications (list + create +
 * verify) and practice contexts (list + create + authorize/revoke/renew), previously with 0 UI.
 * Resolves the caller-held providerPublicId to the numeric registry key (W0) and audits mutations.
 */
@RestController
@RequestMapping("/internal/v1/registry/provider-credentials")
public class ProviderCredentialsBffController {

    private static final Logger log = LoggerFactory.getLogger(ProviderCredentialsBffController.class);
    private static final Set<String> CONTEXT_OPS = Set.of("authorize", "revoke", "renew");

    private final VarapiServiceClient varapiClient;
    private final ProviderRegistryAuditHelper audit;

    public ProviderCredentialsBffController(VarapiServiceClient varapiClient, ProviderRegistryAuditHelper audit) {
        this.varapiClient = varapiClient;
        this.audit = audit;
    }

    // ── Qualifications ──────────────────────────────────────────────────────

    @GetMapping("/provider/{providerPublicId}/qualifications")
    public ResponseEntity<Map<String, Object>> qualifications(
            @PathVariable String providerPublicId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            return ok(varapiClient.getQualificationsByProvider(resolveNumericId(providerPublicId)), requestId, correlationId);
        } catch (Exception e) {
            return degraded(requestId, correlationId, e.getMessage());
        }
    }

    @PostMapping("/provider/{providerPublicId}/qualifications")
    public ResponseEntity<Map<String, Object>> createQualification(
            @PathVariable String providerPublicId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purpose) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>(body);
            payload.put("providerId", resolveNumericId(providerPublicId));
            JsonNode created = varapiClient.createQualification(payload);
            audit.emit("REGISTRY_QUALIFICATION_SUBMITTED", actorId, tenantId, correlationId, purpose,
                    "PROVIDER", providerPublicId, "SUCCESS", body);
            return created(created, requestId, correlationId);
        } catch (Exception e) {
            return error(requestId, correlationId, "QUALIFICATION_CREATE_FAILED", e.getMessage(), HttpStatus.BAD_GATEWAY);
        }
    }

    @PostMapping("/qualifications/{qualificationId}/verify")
    public ResponseEntity<Map<String, Object>> verifyQualification(
            @PathVariable long qualificationId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purpose) {
        try {
            JsonNode result = varapiClient.verifyQualification(qualificationId, body);
            audit.emit("REGISTRY_QUALIFICATION_VERIFIED", actorId, tenantId, correlationId, purpose,
                    "PROVIDER_QUALIFICATION", String.valueOf(qualificationId), "SUCCESS", body);
            return ok(result, requestId, correlationId);
        } catch (Exception e) {
            return error(requestId, correlationId, "QUALIFICATION_VERIFY_FAILED", e.getMessage(), HttpStatus.BAD_GATEWAY);
        }
    }

    // ── Practice contexts ───────────────────────────────────────────────────

    @GetMapping("/provider/{providerPublicId}/practice-contexts")
    public ResponseEntity<Map<String, Object>> practiceContexts(
            @PathVariable String providerPublicId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            return ok(varapiClient.getPracticeContextsByProvider(resolveNumericId(providerPublicId)), requestId, correlationId);
        } catch (Exception e) {
            return degraded(requestId, correlationId, e.getMessage());
        }
    }

    @PostMapping("/provider/{providerPublicId}/practice-contexts")
    public ResponseEntity<Map<String, Object>> createPracticeContext(
            @PathVariable String providerPublicId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purpose) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>(body);
            payload.put("providerId", resolveNumericId(providerPublicId));
            JsonNode created = varapiClient.createPracticeContext(payload);
            audit.emit("REGISTRY_PRACTICE_CONTEXT_CREATED", actorId, tenantId, correlationId, purpose,
                    "PROVIDER", providerPublicId, "SUCCESS", body);
            return created(created, requestId, correlationId);
        } catch (Exception e) {
            return error(requestId, correlationId, "PRACTICE_CONTEXT_CREATE_FAILED", e.getMessage(), HttpStatus.BAD_GATEWAY);
        }
    }

    @PostMapping("/practice-contexts/{contextId}/{op}")
    public ResponseEntity<Map<String, Object>> practiceContextOp(
            @PathVariable long contextId,
            @PathVariable String op,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purpose) {
        String normalized = op == null ? "" : op.toLowerCase();
        if (!CONTEXT_OPS.contains(normalized)) {
            return error(requestId, correlationId, "UNSUPPORTED_OP", "Unsupported practice-context op: " + op, HttpStatus.BAD_REQUEST);
        }
        try {
            JsonNode result = varapiClient.practiceContextOp(contextId, normalized, body != null ? body : Map.of());
            audit.emit("REGISTRY_PRACTICE_CONTEXT_" + normalized.toUpperCase(), actorId, tenantId, correlationId, purpose,
                    "PROVIDER_PRACTICE_CONTEXT", String.valueOf(contextId), "SUCCESS", body != null ? body : Map.of());
            return ok(result, requestId, correlationId);
        } catch (Exception e) {
            return error(requestId, correlationId, "PRACTICE_CONTEXT_OP_FAILED", e.getMessage(), HttpStatus.BAD_GATEWAY);
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
        return ResponseEntity.ok(body(data, requestId, correlationId));
    }

    private static ResponseEntity<Map<String, Object>> created(JsonNode data, String requestId, String correlationId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(body(data, requestId, correlationId));
    }

    private static Map<String, Object> body(JsonNode data, String requestId, String correlationId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("data", data != null ? data : List.of());
        m.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return m;
    }

    private static ResponseEntity<Map<String, Object>> degraded(String requestId, String correlationId, String message) {
        log.warn("Provider credentials degraded: {}", message);
        return ResponseEntity.ok(Map.of(
                "data", List.of(),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId,
                        "degraded", true, "source", "varapi-service", "reason", message != null ? message : "unavailable")));
    }

    private static ResponseEntity<Map<String, Object>> error(String requestId, String correlationId,
                                                             String code, String message, HttpStatus status) {
        return ResponseEntity.status(status).body(Map.of(
                "error", Map.of("code", code, "message", message != null ? message : code),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }
}
