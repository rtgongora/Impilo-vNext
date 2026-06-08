package zw.gov.mohcc.impilo.experience.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;
import zw.gov.mohcc.impilo.experience.client.VarapiServiceClient;

import java.util.*;

/**
 * Registry endpoints.
 * GET /internal/v1/registry/providers — list providers with search filter, pagination.
 * GET /internal/v1/registry/providers/{id} — get single provider.
 * GET /internal/v1/registry/facilities — list facilities (duplicate access path for registry zone).
 */
@RestController
@RequestMapping("/internal/v1/registry")
public class RegistryController {

    private static final Logger log = LoggerFactory.getLogger(RegistryController.class);

    private final VarapiServiceClient varapiClient;
    private final TusoServiceClient tusoClient;

    public RegistryController(VarapiServiceClient varapiClient, TusoServiceClient tusoClient) {
        this.varapiClient = varapiClient;
        this.tusoClient = tusoClient;
    }

    @GetMapping("/providers")
    public ResponseEntity<Map<String, Object>> listProviders(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("query", search != null ? search : "");
            body.put("profession", null);
            body.put("status", status);
            body.put("page", page);
            body.put("size", size);
            JsonNode paged = varapiClient.searchProviders(body);
            List<Map<String, Object>> rows = new ArrayList<>();
            if (paged != null && paged.has("items") && paged.get("items").isArray()) {
                for (JsonNode item : paged.get("items")) {
                    rows.add(mapProviderSummaryToResource(item));
                }
            }
            return ResponseEntity.ok(Map.of(
                    "data", rows,
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId,
                            "page", page, "size", size)));
        } catch (Exception e) {
            log.warn("VARAPI provider search failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "data", List.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId,
                            "page", page, "size", size)));
        }
    }

    @GetMapping("/providers/{id}")
    public ResponseEntity<Map<String, Object>> getProvider(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = varapiClient.getProvider(id);
            return ResponseEntity.ok(Map.of(
                    "data", data != null ? data : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "data", Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    @PostMapping("/providers/{id}/status")
    public ResponseEntity<Map<String, Object>> changeProviderStatus(
            @PathVariable String id,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            if (!body.containsKey("newStatus") && body.containsKey("status")) {
                body.put("newStatus", body.get("status"));
            }
            JsonNode data = varapiClient.changeProviderStatus(id, body);
            return ResponseEntity.ok(Map.of(
                    "data", data != null ? data : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("VARAPI provider status change failed: {}", e.getMessage());
            return ResponseEntity.status(502).body(Map.of(
                    "error", Map.of("code", "VARAPI_UNAVAILABLE", "message", e.getMessage()),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    @GetMapping("/facilities")
    public ResponseEntity<Map<String, Object>> listFacilities(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, name = "facility_type") String facilityType,
            @RequestParam(required = false) String province,
            @RequestParam(required = false) String search) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("query", search != null ? search : "");
            body.put("facilityType", facilityType);
            body.put("status", status);
            body.put("province", province);
            body.put("district", null);
            body.put("level", null);
            body.put("page", page);
            body.put("size", Math.min(Math.max(size, 1), 200));
            JsonNode paged = tusoClient.searchFacilities(body);
            List<Map<String, Object>> rows = new ArrayList<>();
            if (paged != null && paged.has("items") && paged.get("items").isArray()) {
                for (JsonNode item : paged.get("items")) {
                    rows.add(mapRegistryFacilitySummary(item));
                }
            }
            return ResponseEntity.ok(Map.of(
                    "data", rows,
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId,
                            "page", page, "size", size)));
        } catch (Exception e) {
            log.warn("TUSO facility search (registry path) failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "data", List.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId,
                            "page", page, "size", size)));
        }
    }

    private static Map<String, Object> mapProviderSummaryToResource(JsonNode n) {
        String publicId = text(n, "providerPublicId");
        String title = text(n, "title");
        String given = text(n, "givenName");
        String family = text(n, "familyName");
        String display = (title + " " + given + " " + family).trim().replaceAll("\\s+", " ");
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("displayName", display.isEmpty() ? publicId : display);
        attrs.put("registrationNumber", text(n, "practiceNumber"));
        attrs.put("speciality", text(n, "profession"));
        attrs.put("status", text(n, "status"));
        attrs.put("impiloHealthId", n.has("impiloHealthId") && !n.get("impiloHealthId").isNull()
                ? n.get("impiloHealthId").asText()
                : null);
        return Map.of("id", publicId, "type", "provider", "attributes", attrs);
    }

    private static Map<String, Object> mapRegistryFacilitySummary(JsonNode n) {
        String idStr = n.has("id") ? String.valueOf(n.get("id").asLong()) : "";
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("name", text(n, "name"));
        attrs.put("code", text(n, "code"));
        attrs.put("facilityType", text(n, "type"));
        attrs.put("district", text(n, "district"));
        attrs.put("province", text(n, "province"));
        attrs.put("status", text(n, "status"));
        return Map.of("id", idStr, "type", "facility", "attributes", attrs);
    }

    private static String text(JsonNode n, String field) {
        if (n == null || !n.has(field) || n.get(field).isNull()) {
            return "";
        }
        return n.get(field).asText("");
    }


    /**
     * GET /internal/v1/registry/providers/{id}/licenses
     *
     * Fetches license history for a provider from VARAPI.
     */
    @GetMapping("/providers/{id}/licenses")
    public ResponseEntity<Map<String, Object>> getProviderLicenses(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode licenses = varapiClient.getProviderLicenses(id);
            return ResponseEntity.ok(Map.of(
                    "data", licenses != null ? licenses : new Object[0],
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "data", new Object[0],
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    /**
     * GET /internal/v1/registry/providers/{id}/affiliations
     *
     * Fetches council affiliations for a provider from VARAPI.
     */
    @GetMapping("/providers/{id}/affiliations")
    public ResponseEntity<Map<String, Object>> getProviderAffiliations(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode affiliations = varapiClient.getProviderCouncilAffiliations(id);
            return ResponseEntity.ok(Map.of(
                    "data", affiliations != null ? affiliations : new Object[0],
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "data", new Object[0],
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    /**
     * GET /internal/v1/registry/providers/{id}/cpd
     *
     * Fetches CPD summary (cycles, earned/required points) from VARAPI.
     */
    @GetMapping("/providers/{id}/cpd")
    public ResponseEntity<Map<String, Object>> getProviderCpd(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode cpd = varapiClient.getProviderCpdSummary(id);
            return ResponseEntity.ok(Map.of(
                    "data", cpd != null ? cpd : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "data", Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    /**
     * GET /internal/v1/registry/providers/{id}/privileges
     *
     * Fetches provider-facility privileges (affiliations) from VARAPI.
     */
    @GetMapping("/providers/{id}/privileges")
    public ResponseEntity<Map<String, Object>> getProviderPrivileges(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode privileges = varapiClient.getProviderPrivileges(id);
            return ResponseEntity.ok(Map.of(
                    "data", privileges != null ? privileges : new Object[0],
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "data", new Object[0],
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    /**
     * GET /internal/v1/registry/reconciliation/queue — Varapi council import reconciliation queue.
     */
    @GetMapping("/reconciliation/queue")
    public ResponseEntity<Map<String, Object>> getReconciliationQueue(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode rows = varapiClient.getReconciliationQueue(status, page, size);
            return ResponseEntity.ok(Map.of(
                    "data", rows != null ? rows : JsonNodeFactory.instance.objectNode(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "data", JsonNodeFactory.instance.objectNode(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    @PostMapping("/reconciliation/{caseId}/decision")
    public ResponseEntity<Map<String, Object>> decideReconciliationCase(
            @PathVariable long caseId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode result = varapiClient.decideReconciliationCase(caseId, body);
            return ResponseEntity.ok(Map.of(
                    "data", result != null ? result : JsonNodeFactory.instance.objectNode(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", Map.of("code", "VARAPI_UNAVAILABLE", "message", e.getMessage()),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    /**
     * GET /internal/v1/registry/provider-council/obligations — MusheX-linked council fee obligations.
     */
    @GetMapping("/provider-council/obligations")
    public ResponseEntity<Map<String, Object>> getProviderCouncilObligations(
            @RequestParam long providerId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode rows = varapiClient.getProviderCouncilObligations(providerId);
            return ResponseEntity.ok(Map.of(
                    "data", rows != null ? rows : JsonNodeFactory.instance.arrayNode(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "data", JsonNodeFactory.instance.arrayNode(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    /**
     * GET /internal/v1/registry/provider-council/applications/open — council staff queue.
     */
    @GetMapping("/provider-council/applications/open")
    public ResponseEntity<Map<String, Object>> getProviderCouncilOpenApplications(
            @RequestParam long councilId,
            @RequestParam(required = false) String workflowStates,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode rows = varapiClient.getProviderCouncilOpenApplications(councilId, workflowStates);
            return ResponseEntity.ok(Map.of(
                    "data", rows != null ? rows : JsonNodeFactory.instance.arrayNode(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "data", JsonNodeFactory.instance.arrayNode(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    /**
     * GET /internal/v1/registry/provider-council/fundo-cpd-candidates — governed Fundo → CPD pipeline.
     */
    @GetMapping("/provider-council/fundo-cpd-candidates")
    public ResponseEntity<Map<String, Object>> getFundoCpdCandidates(
            @RequestParam long providerId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode rows = varapiClient.getFundoCpdCandidates(providerId);
            return ResponseEntity.ok(Map.of(
                    "data", rows != null ? rows : JsonNodeFactory.instance.arrayNode(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "data", JsonNodeFactory.instance.arrayNode(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    /**
     * GET /internal/v1/registry/provider-council/council-regulatory-config — per-council fees/CPD/workflow JSON.
     */
    @GetMapping("/provider-council/council-regulatory-config")
    public ResponseEntity<Map<String, Object>> getCouncilRegulatoryConfig(
            @RequestParam long councilId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode row = varapiClient.getCouncilRegulatoryConfig(councilId);
            return ResponseEntity.ok(Map.of(
                    "data", row != null ? row : JsonNodeFactory.instance.objectNode(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "data", JsonNodeFactory.instance.objectNode(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    @PostMapping("/provider-council/obligations/{obligationId}/mushex-intent")
    public ResponseEntity<Map<String, Object>> createCouncilMusheXIntent(
            @PathVariable long obligationId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode row = varapiClient.createMusheXIntentForObligation(obligationId);
            return ResponseEntity.ok(Map.of(
                    "data", row != null ? row : JsonNodeFactory.instance.objectNode(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", Map.of("code", "VARAPI_UNAVAILABLE", "message", e.getMessage()),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    @PostMapping("/provider-council/obligations/{obligationId}/sync-payment")
    public ResponseEntity<Map<String, Object>> syncCouncilObligationPayment(
            @PathVariable long obligationId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode row = varapiClient.syncCouncilObligationPayment(obligationId);
            return ResponseEntity.ok(Map.of(
                    "data", row != null ? row : JsonNodeFactory.instance.objectNode(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", Map.of("code", "VARAPI_UNAVAILABLE", "message", e.getMessage()),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    @PostMapping("/provider-council/applications/{applicationId}/under-admin-review")
    public ResponseEntity<Map<String, Object>> advanceCouncilApplicationToUnderAdminReview(
            @PathVariable long applicationId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode row = varapiClient.advanceCouncilApplicationToUnderAdminReview(applicationId);
            return ResponseEntity.ok(Map.of(
                    "data", row != null ? row : JsonNodeFactory.instance.objectNode(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", Map.of("code", "VARAPI_UNAVAILABLE", "message", e.getMessage()),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    @PostMapping("/provider-council/applications/{applicationId}/awaiting-payment")
    public ResponseEntity<Map<String, Object>> advanceCouncilApplicationToAwaitingPayment(
            @PathVariable long applicationId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode row = varapiClient.advanceCouncilApplicationToAwaitingPayment(applicationId);
            return ResponseEntity.ok(Map.of(
                    "data", row != null ? row : JsonNodeFactory.instance.objectNode(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", Map.of("code", "VARAPI_UNAVAILABLE", "message", e.getMessage()),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    @PostMapping("/provider-council/applications/{applicationId}/fee-paid")
    public ResponseEntity<Map<String, Object>> advanceCouncilApplicationAfterFeePaid(
            @PathVariable long applicationId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode row = varapiClient.advanceCouncilApplicationAfterFeePaid(applicationId);
            return ResponseEntity.ok(Map.of(
                    "data", row != null ? row : JsonNodeFactory.instance.objectNode(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", Map.of("code", "VARAPI_UNAVAILABLE", "message", e.getMessage()),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    @PostMapping("/provider-council/reviews")
    public ResponseEntity<Map<String, Object>> recordCouncilReview(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode row = varapiClient.recordCouncilReview(body);
            return ResponseEntity.ok(Map.of(
                    "data", row != null ? row : JsonNodeFactory.instance.objectNode(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", Map.of("code", "VARAPI_UNAVAILABLE", "message", e.getMessage()),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    @PostMapping("/provider-council/fundo-cpd-candidates/{candidateId}/accept")
    public ResponseEntity<Map<String, Object>> acceptFundoCpdCandidate(
            @PathVariable long candidateId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode row = varapiClient.acceptFundoCpdCandidate(candidateId);
            return ResponseEntity.ok(Map.of(
                    "data", row != null ? row : JsonNodeFactory.instance.objectNode(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", Map.of("code", "VARAPI_UNAVAILABLE", "message", e.getMessage()),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    @PostMapping("/provider-council/fundo-cpd-candidates/{candidateId}/reject")
    public ResponseEntity<Map<String, Object>> rejectFundoCpdCandidate(
            @PathVariable long candidateId,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode row = varapiClient.rejectFundoCpdCandidate(candidateId, body != null ? body : Map.of());
            return ResponseEntity.ok(Map.of(
                    "data", row != null ? row : JsonNodeFactory.instance.objectNode(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", Map.of("code", "VARAPI_UNAVAILABLE", "message", e.getMessage()),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    /**
     * PUT /internal/v1/registry/provider-council/council-regulatory-config — upsert council regulatory config.
     */
    @PutMapping("/provider-council/council-regulatory-config")
    public ResponseEntity<Map<String, Object>> putCouncilRegulatoryConfig(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode row = varapiClient.putCouncilRegulatoryConfig(body);
            return ResponseEntity.ok(Map.of(
                    "data", row != null ? row : JsonNodeFactory.instance.objectNode(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "data", JsonNodeFactory.instance.objectNode(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    /**
     * GET /internal/v1/registry/providers/{id}/work-context
     *
     * <p>Aggregates VARAPI provider read surfaces with optional TUSO shift context for the signed-in actor.</p>
     */
    @GetMapping("/providers/{id}/work-context")
    public ResponseEntity<Map<String, Object>> getProviderWorkContext(
            @PathVariable String id,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorHealthId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        try {
            payload.put("profile", varapiClient.getProvider(id));
        } catch (Exception e) {
            log.debug("work-context profile miss: {}", e.getMessage());
            payload.put("profile", null);
        }
        try {
            payload.put("privileges", varapiClient.getProviderPrivileges(id));
        } catch (Exception e) {
            payload.put("privileges", JsonNodeFactory.instance.arrayNode());
        }
        try {
            payload.put("licenses", varapiClient.getProviderLicenses(id));
        } catch (Exception e) {
            payload.put("licenses", JsonNodeFactory.instance.arrayNode());
        }
        if (actorHealthId != null && !actorHealthId.isBlank()) {
            try {
                payload.put("currentShift", tusoClient.getCurrentShift(actorHealthId));
            } catch (Exception e) {
                payload.put("currentShift", null);
            }
        } else {
            payload.put("currentShift", null);
        }
        return ResponseEntity.ok(Map.of(
                "data", payload,
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }
}
