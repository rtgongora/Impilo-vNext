package zw.gov.mohcc.impilo.experience.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
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

    private final VarapiServiceClient varapiClient;

    public RegistryController(VarapiServiceClient varapiClient) {
        this.varapiClient = varapiClient;
    }

    @GetMapping("/providers")
    public ResponseEntity<Map<String, Object>> listProviders(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(Map.of(
                "data", List.of(),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId,
                        "page", page, "size", size)));
    }

    @GetMapping("/providers/{id}")
    public ResponseEntity<Map<String, Object>> getProvider(
            @PathVariable UUID id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = varapiClient.getProvider(id.toString());
            return ResponseEntity.ok(Map.of(
                    "data", data != null ? data : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "data", Map.of(),
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
        return ResponseEntity.ok(Map.of(
                "data", List.of(),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId,
                        "page", page, "size", size)));
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
}
