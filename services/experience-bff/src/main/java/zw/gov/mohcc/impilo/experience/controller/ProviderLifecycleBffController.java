package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.VarapiServiceClient;
import zw.gov.mohcc.impilo.experience.providerregistry.ProviderRegistryAuditHelper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Operator lifecycle console for a provider — the curated 18-state transition surface (R2/G10).
 * Surfaces the VARAPI canonical transition endpoints (previously the 18-state engine was reduced
 * to a single 4-value status change at the experience layer). Audits every transition; terminal
 * transitions (RETIRED/DECEASED/REMOVED) additionally require an approver-tier role, enforced by
 * the gateway rego — this controller records the governance audit.
 */
@RestController
@RequestMapping("/internal/v1/registry/providers/{providerPublicId}/lifecycle")
public class ProviderLifecycleBffController {

    private static final Logger log = LoggerFactory.getLogger(ProviderLifecycleBffController.class);
    private static final Set<String> TERMINAL = Set.of("RETIRED", "DECEASED", "REMOVED");

    private final VarapiServiceClient varapiClient;
    private final ProviderRegistryAuditHelper audit;

    public ProviderLifecycleBffController(VarapiServiceClient varapiClient, ProviderRegistryAuditHelper audit) {
        this.varapiClient = varapiClient;
        this.audit = audit;
    }

    @GetMapping("/transitions")
    public ResponseEntity<Map<String, Object>> transitions(
            @PathVariable String providerPublicId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = varapiClient.getLifecycleTransitions(providerPublicId);
            return ok(data, requestId, correlationId);
        } catch (Exception e) {
            log.warn("Lifecycle transitions failed for {}: {}", providerPublicId, e.getMessage());
            return degraded(requestId, correlationId, e.getMessage());
        }
    }

    @PostMapping("/transitions")
    public ResponseEntity<Map<String, Object>> transition(
            @PathVariable String providerPublicId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purpose) {
        String target = body.get("targetState") != null ? String.valueOf(body.get("targetState")).toUpperCase() : "";
        String reason = body.get("reason") != null ? String.valueOf(body.get("reason")) : "";
        if (target.isBlank()) {
            return error(requestId, correlationId, "TARGET_REQUIRED", "A target lifecycle state is required.", HttpStatus.BAD_REQUEST);
        }
        if (TERMINAL.contains(target) && reason.isBlank()) {
            return error(requestId, correlationId, "REASON_REQUIRED", "A reason is required to " + target.toLowerCase() + " a provider.", HttpStatus.BAD_REQUEST);
        }
        try {
            JsonNode data = varapiClient.transitionLifecycle(providerPublicId, body);
            audit.emit("REGISTRY_LIFECYCLE_" + target, actorId, tenantId, correlationId, purpose,
                    "PROVIDER", providerPublicId, "SUCCESS", body);
            return ok(data, requestId, correlationId);
        } catch (HttpStatusCodeException e) {
            // e.g. an invalid transition (409) or a validation failure (400) — pass through honestly.
            audit.emit("REGISTRY_LIFECYCLE_" + target, actorId, tenantId, correlationId, purpose,
                    "PROVIDER", providerPublicId, "DENIED", body);
            return error(requestId, correlationId, "TRANSITION_REJECTED", e.getResponseBodyAsString(),
                    e.getStatusCode().is4xxClientError() ? HttpStatus.valueOf(e.getStatusCode().value()) : HttpStatus.BAD_GATEWAY);
        } catch (Exception e) {
            return error(requestId, correlationId, "TRANSITION_FAILED", e.getMessage(), HttpStatus.BAD_GATEWAY);
        }
    }

    @PostMapping("/licence-renewal")
    public ResponseEntity<Map<String, Object>> startRenewal(
            @PathVariable String providerPublicId,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purpose) {
        return renewalAction(providerPublicId, true, tenantId, requestId, correlationId, actorId, purpose);
    }

    @PostMapping("/licence-restoration")
    public ResponseEntity<Map<String, Object>> startRestoration(
            @PathVariable String providerPublicId,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purpose) {
        return renewalAction(providerPublicId, false, tenantId, requestId, correlationId, actorId, purpose);
    }

    private ResponseEntity<Map<String, Object>> renewalAction(
            String providerPublicId, boolean renewal, String tenantId, String requestId,
            String correlationId, String actorId, String purpose) {
        try {
            long licenceId = resolveActiveLicenceId(providerPublicId);
            JsonNode data = renewal
                    ? varapiClient.startLicenseRenewal(providerPublicId, licenceId)
                    : varapiClient.startLicenseRestoration(providerPublicId, licenceId);
            audit.emit(renewal ? "REGISTRY_LICENCE_RENEWAL_STARTED" : "REGISTRY_LICENCE_RESTORATION_STARTED",
                    actorId, tenantId, correlationId, purpose, "PROVIDER", providerPublicId, "SUCCESS",
                    Map.of("licenceId", String.valueOf(licenceId)));
            return ok(data, requestId, correlationId);
        } catch (HttpStatusCodeException e) {
            return error(requestId, correlationId, "RENEWAL_REJECTED", e.getResponseBodyAsString(),
                    e.getStatusCode().is4xxClientError() ? HttpStatus.valueOf(e.getStatusCode().value()) : HttpStatus.BAD_GATEWAY);
        } catch (Exception e) {
            return error(requestId, correlationId, "NO_ACTIVE_LICENCE", e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    /** Find the provider's ACTIVE licence id (the sweep changes provider lifecycle, not licence status). */
    private long resolveActiveLicenceId(String providerPublicId) {
        JsonNode licences = varapiClient.getProviderLicenses(providerPublicId);
        if (licences != null && licences.isArray()) {
            for (JsonNode l : licences) {
                if ("ACTIVE".equalsIgnoreCase(l.path("status").asText(""))) {
                    return l.path("id").asLong();
                }
            }
            // fall back to the first licence if none is explicitly ACTIVE
            if (licences.size() > 0) {
                return licences.get(0).path("id").asLong();
            }
        }
        throw new IllegalStateException("No licence found for provider " + providerPublicId);
    }

    private static ResponseEntity<Map<String, Object>> ok(JsonNode data, String requestId, String correlationId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("data", data != null ? data : Map.of());
        m.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(m);
    }

    private static ResponseEntity<Map<String, Object>> degraded(String requestId, String correlationId, String message) {
        return ResponseEntity.ok(Map.of(
                "data", Map.of(),
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
