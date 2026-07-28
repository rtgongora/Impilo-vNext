package zw.gov.mohcc.impilo.experience.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;
import zw.gov.mohcc.impilo.experience.support.JsonApiRows;
import zw.gov.mohcc.impilo.experience.client.VarapiServiceClient;
import zw.gov.mohcc.impilo.experience.support.BffDegradedMeta;

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
                    "meta", BffDegradedMeta.withPaging(requestId, correlationId, page, size)));
        } catch (Exception e) {
            log.warn("VARAPI provider search failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "data", List.of(),
                    "meta", BffDegradedMeta.degradedWithPaging(
                            requestId,
                            correlationId,
                            "varapi-service",
                            "Provider registry is temporarily unavailable. Retry shortly or open Provider onboarding to register a new provider.",
                            page,
                            size)));
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
            log.warn("VARAPI provider detail failed for id={}: {}", id, e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "data", Map.of(),
                    "meta", BffDegradedMeta.degraded(
                            requestId,
                            correlationId,
                            "varapi-service",
                            "Provider profile could not be loaded. Confirm the Provider ID exists or retry when VARAPI is available.")));
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
                    "meta", BffDegradedMeta.withPaging(requestId, correlationId, page, size)));
        } catch (Exception e) {
            log.warn("TUSO facility search (registry path) failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "data", List.of(),
                    "meta", BffDegradedMeta.degradedWithPaging(
                            requestId,
                            correlationId,
                            "tuso-service",
                            "Facility registry is temporarily unavailable. Select a facility from session context or retry when TUSO is reachable.",
                            page,
                            size)));
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
     * A failed VARAPI/TUSO call must not render as an empty registry record. "This provider holds
     * no licence", "no council affiliation", "no privileges at this facility" are all affirmative
     * regulatory findings — the ones that decide whether someone may practise — and returning them
     * because a downstream was unreachable is a fabricated answer to a trust question.
     *
     * <p>The provider/facility search paths above deliberately keep their 200 and mark
     * {@code meta.degraded} via {@link BffDegradedMeta} instead; a partial result list is still
     * usable, and the flag is on the response for callers to honour.</p>
     */
    private static ResponseEntity<Map<String, Object>> registryUnavailable(
            String code, String message, String requestId, String correlationId) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "error", code,
                "message", message,
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
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
            log.error("VARAPI getProviderLicenses failed for provider={}: {}", id, e.getMessage());
            return registryUnavailable("provider_licenses_unavailable",
                    "Provider licences could not be retrieved. Do not treat this as an absence of licences.",
                    requestId, correlationId);
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
            log.error("VARAPI getProviderCouncilAffiliations failed for provider={}: {}", id, e.getMessage());
            return registryUnavailable("provider_affiliations_unavailable",
                    "Council affiliations could not be retrieved. Do not treat this as an absence of affiliations.",
                    requestId, correlationId);
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
            log.error("VARAPI getProviderCpdSummary failed for provider={}: {}", id, e.getMessage());
            return registryUnavailable("provider_cpd_unavailable",
                    "The CPD summary could not be retrieved. Do not treat this as an absence of CPD.",
                    requestId, correlationId);
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
            log.error("VARAPI getProviderPrivileges failed for provider={}: {}", id, e.getMessage());
            return registryUnavailable("provider_privileges_unavailable",
                    "Provider privileges could not be retrieved. Do not treat this as an absence of privileges.",
                    requestId, correlationId);
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
            // useRegistry declares attributes on these rows; VARAPI returns its own entities.
            return ResponseEntity.ok(Map.of(
                    "data", JsonApiRows.rows(rows, "reconciliation_case", "caseId", "case_id", "id"),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("VARAPI getReconciliationQueue failed: {}", e.getMessage());
            return registryUnavailable("reconciliation_queue_unavailable",
                    "The reconciliation queue could not be retrieved. Do not treat this as an empty queue.",
                    requestId, correlationId);
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
            log.error("VARAPI getProviderCouncilObligations failed for provider={}: {}", providerId, e.getMessage());
            return registryUnavailable("council_obligations_unavailable",
                    "Council obligations could not be retrieved. Do not treat this as an absence of obligations.",
                    requestId, correlationId);
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
            log.error("VARAPI getProviderCouncilOpenApplications failed for council={}: {}", councilId, e.getMessage());
            return registryUnavailable("council_applications_unavailable",
                    "Open council applications could not be retrieved. Do not treat this as an empty queue.",
                    requestId, correlationId);
        }
    }

    /**
     * GET /internal/v1/registry/provider-council/fundo-cpd-candidates — governed Fundo → CPD pipeline.
     */
    @GetMapping("/provider-council/fundo-cpd-candidates")
    public ResponseEntity<Map<String, Object>> getFundoCpdCandidates(
            @RequestParam(required = false) Long providerId,
            @RequestParam(required = false) String providerPublicId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        if (providerId == null && (providerPublicId == null || providerPublicId.isBlank())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "MISSING_PARAM",
                            "message", "providerId or providerPublicId is required"),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
        try {
            JsonNode rows = providerId != null
                    ? varapiClient.getFundoCpdCandidates(providerId)
                    : varapiClient.getFundoCpdCandidatesByPublicId(providerPublicId);
            return ResponseEntity.ok(Map.of(
                    "data", rows != null ? rows : JsonNodeFactory.instance.arrayNode(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("VARAPI getFundoCpdCandidates failed: {}", e.getMessage());
            return registryUnavailable("fundo_cpd_candidates_unavailable",
                    "Fundo CPD candidates could not be retrieved. Do not treat this as an absence of candidates.",
                    requestId, correlationId);
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
            log.error("VARAPI getCouncilRegulatoryConfig failed for council={}: {}", councilId, e.getMessage());
            return registryUnavailable("council_config_unavailable",
                    "The council regulatory configuration could not be retrieved. Do not treat this as an unconfigured council.",
                    requestId, correlationId);
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
            // A 200 for a rejected write tells the council its fee and CPD rules were saved.
            log.error("VARAPI putCouncilRegulatoryConfig failed: {}", e.getMessage());
            return registryUnavailable("council_config_not_saved",
                    "The council regulatory configuration could not be saved.",
                    requestId, correlationId);
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
        // This composes four independent sources, so failing the whole call would take the work
        // context away over one bad source. Instead each source that could not be read is named
        // in meta.unavailable_sources — an empty licences or privileges array here is otherwise
        // indistinguishable from a provider who genuinely holds neither, which is the difference
        // between "not permitted" and "we could not check".
        Map<String, Object> payload = new LinkedHashMap<>();
        List<String> unavailableSources = new ArrayList<>();
        try {
            payload.put("profile", varapiClient.getProvider(id));
        } catch (Exception e) {
            log.error("work-context profile unavailable for provider={}: {}", id, e.getMessage());
            unavailableSources.add("profile");
            payload.put("profile", null);
        }
        try {
            payload.put("privileges", varapiClient.getProviderPrivileges(id));
        } catch (Exception e) {
            log.error("work-context privileges unavailable for provider={}: {}", id, e.getMessage());
            unavailableSources.add("privileges");
            payload.put("privileges", JsonNodeFactory.instance.arrayNode());
        }
        try {
            payload.put("licenses", varapiClient.getProviderLicenses(id));
        } catch (Exception e) {
            log.error("work-context licenses unavailable for provider={}: {}", id, e.getMessage());
            unavailableSources.add("licenses");
            payload.put("licenses", JsonNodeFactory.instance.arrayNode());
        }
        if (actorHealthId != null && !actorHealthId.isBlank()) {
            try {
                payload.put("currentShift", tusoClient.getCurrentShift(actorHealthId));
            } catch (Exception e) {
                log.error("work-context shift unavailable for actor={}: {}",
                        actorHealthId, e.getMessage());
                unavailableSources.add("currentShift");
                payload.put("currentShift", null);
            }
        } else {
            payload.put("currentShift", null);
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("request_id", requestId);
        meta.put("correlation_id", correlationId);
        meta.put("unavailable_sources", unavailableSources);
        return ResponseEntity.ok(Map.of("data", payload, "meta", meta));
    }
}
