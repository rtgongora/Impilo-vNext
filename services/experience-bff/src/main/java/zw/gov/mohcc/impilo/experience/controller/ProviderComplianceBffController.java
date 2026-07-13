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
 * Registrar compliance + disciplinary casework for a provider (R2/G9). Surfaces the VARAPI
 * ComplianceController (previously unwired) and the new disciplinary case engine, resolving the
 * caller-held providerPublicId to the numeric registry key (W0) and auditing every mutation.
 * Authorization (incl. disciplinary-narrative visibility) is enforced at the gateway rego, as with
 * the rest of the registry surface; BFF-level field masking is a documented follow-up.
 */
@RestController
@RequestMapping("/internal/v1/registry/provider-compliance")
public class ProviderComplianceBffController {

    private static final Logger log = LoggerFactory.getLogger(ProviderComplianceBffController.class);
    private static final Set<String> COMPLIANCE_OPS = Set.of("fulfil", "exempt", "escalate");

    private final VarapiServiceClient varapiClient;
    private final ProviderRegistryAuditHelper audit;

    public ProviderComplianceBffController(VarapiServiceClient varapiClient, ProviderRegistryAuditHelper audit) {
        this.varapiClient = varapiClient;
        this.audit = audit;
    }

    // ── Compliance ──────────────────────────────────────────────────────────

    @GetMapping("/provider/{providerPublicId}/compliance")
    public ResponseEntity<Map<String, Object>> compliance(
            @PathVariable String providerPublicId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            return ok(varapiClient.getComplianceByProvider(resolveNumericId(providerPublicId)), requestId, correlationId);
        } catch (Exception e) {
            return degraded(requestId, correlationId, e.getMessage());
        }
    }

    @PostMapping("/provider/{providerPublicId}/compliance")
    public ResponseEntity<Map<String, Object>> createCompliance(
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
            JsonNode created = varapiClient.createComplianceAction(payload);
            audit.emit("REGISTRY_COMPLIANCE_ACTION_CREATED", actorId, tenantId, correlationId, purpose,
                    "PROVIDER", providerPublicId, "SUCCESS", body);
            return created(created, requestId, correlationId);
        } catch (Exception e) {
            return error(requestId, correlationId, "COMPLIANCE_CREATE_FAILED", e.getMessage(), HttpStatus.BAD_GATEWAY);
        }
    }

    @PostMapping("/compliance/{actionId}/{op}")
    public ResponseEntity<Map<String, Object>> complianceOp(
            @PathVariable long actionId,
            @PathVariable String op,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purpose) {
        String normalized = op == null ? "" : op.toLowerCase();
        if (!COMPLIANCE_OPS.contains(normalized)) {
            return error(requestId, correlationId, "UNSUPPORTED_OP", "Unsupported compliance op: " + op, HttpStatus.BAD_REQUEST);
        }
        try {
            JsonNode result = varapiClient.complianceActionOp(actionId, normalized, body != null ? body : Map.of());
            audit.emit("REGISTRY_COMPLIANCE_" + normalized.toUpperCase(), actorId, tenantId, correlationId, purpose,
                    "PROVIDER_COMPLIANCE", String.valueOf(actionId), "SUCCESS", body != null ? body : Map.of());
            return ok(result, requestId, correlationId);
        } catch (Exception e) {
            return error(requestId, correlationId, "COMPLIANCE_OP_FAILED", e.getMessage(), HttpStatus.BAD_GATEWAY);
        }
    }

    // ── Disciplinary ────────────────────────────────────────────────────────

    @GetMapping("/provider/{providerPublicId}/disciplinary")
    public ResponseEntity<Map<String, Object>> disciplinary(
            @PathVariable String providerPublicId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            return ok(varapiClient.getDisciplinaryByProvider(resolveNumericId(providerPublicId)), requestId, correlationId);
        } catch (Exception e) {
            return degraded(requestId, correlationId, e.getMessage());
        }
    }

    @PostMapping("/provider/{providerPublicId}/disciplinary")
    public ResponseEntity<Map<String, Object>> openDisciplinary(
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
            JsonNode created = varapiClient.openDisciplinaryCase(payload);
            audit.emit("REGISTRY_DISCIPLINARY_CASE_OPENED", actorId, tenantId, correlationId, purpose,
                    "PROVIDER", providerPublicId, "SUCCESS", body);
            return created(created, requestId, correlationId);
        } catch (Exception e) {
            return error(requestId, correlationId, "DISCIPLINARY_OPEN_FAILED", e.getMessage(), HttpStatus.BAD_GATEWAY);
        }
    }

    @PostMapping("/disciplinary/{caseId}/record-action")
    public ResponseEntity<Map<String, Object>> recordAction(
            @PathVariable long caseId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purpose) {
        try {
            JsonNode result = varapiClient.recordDisciplinaryAction(caseId, body);
            audit.emit("REGISTRY_DISCIPLINARY_ACTION_RECORDED", actorId, tenantId, correlationId, purpose,
                    "PROVIDER_DISCIPLINARY", String.valueOf(caseId), "SUCCESS", body);
            return ok(result, requestId, correlationId);
        } catch (Exception e) {
            return error(requestId, correlationId, "DISCIPLINARY_ACTION_FAILED", e.getMessage(), HttpStatus.BAD_GATEWAY);
        }
    }

    @PostMapping("/disciplinary/{caseId}/close")
    public ResponseEntity<Map<String, Object>> closeDisciplinary(
            @PathVariable long caseId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purpose) {
        try {
            JsonNode result = varapiClient.closeDisciplinaryCase(caseId, body);
            audit.emit("REGISTRY_DISCIPLINARY_CASE_CLOSED", actorId, tenantId, correlationId, purpose,
                    "PROVIDER_DISCIPLINARY", String.valueOf(caseId), "SUCCESS", body);
            return ok(result, requestId, correlationId);
        } catch (Exception e) {
            return error(requestId, correlationId, "DISCIPLINARY_CLOSE_FAILED", e.getMessage(), HttpStatus.BAD_GATEWAY);
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
        log.warn("Provider compliance/disciplinary degraded: {}", message);
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
