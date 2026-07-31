package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.WorkforceGovernanceClient;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/internal/v1/workforce-governance")
public class WorkforceGovernanceController {

    private static final Logger log = LoggerFactory.getLogger(WorkforceGovernanceController.class);

    private final WorkforceGovernanceClient workforceGovernanceClient;

    public WorkforceGovernanceController(WorkforceGovernanceClient workforceGovernanceClient) {
        this.workforceGovernanceClient = workforceGovernanceClient;
    }

    @GetMapping("/organisations")
    public ResponseEntity<Map<String, Object>> listOrganisations(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = workforceGovernanceClient.listOrganisations();
            return ok(requestId, correlationId, data);
        } catch (Exception e) {
            log.warn("listOrganisations failed: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "error", e.getMessage(), "requestId", requestId, "correlationId", correlationId));
        }
    }

    @GetMapping("/organisations/{id}/summary")
    public ResponseEntity<Map<String, Object>> organisationSummary(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String id) {
        try {
            JsonNode data = workforceGovernanceClient.organisationSummary(id);
            return ok(requestId, correlationId, data);
        } catch (Exception e) {
            log.warn("organisationSummary failed: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "error", e.getMessage(), "requestId", requestId, "correlationId", correlationId));
        }
    }

    @GetMapping("/assignments/search")
    public ResponseEntity<Map<String, Object>> searchAssignments(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(required = false) String subjectType,
            @RequestParam(required = false) String subjectId,
            @RequestParam(required = false) String status) {
        try {
            JsonNode data = workforceGovernanceClient.searchAssignments(subjectType, subjectId, status);
            return ok(requestId, correlationId, data);
        } catch (Exception e) {
            log.warn("searchAssignments failed: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "error", e.getMessage(), "requestId", requestId, "correlationId", correlationId));
        }
    }

    @GetMapping("/jurisdictions")
    public ResponseEntity<Map<String, Object>> listJurisdictions(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = workforceGovernanceClient.listJurisdictions();
            return ok(requestId, correlationId, data);
        } catch (Exception e) {
            log.warn("listJurisdictions failed: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "error", e.getMessage(), "requestId", requestId, "correlationId", correlationId));
        }
    }

    @GetMapping("/facilities/{facilityId}/governance-summary")
    public ResponseEntity<Map<String, Object>> facilityGovernanceSummary(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable long facilityId) {
        try {
            JsonNode data = workforceGovernanceClient.facilityGovernanceSummary(facilityId);
            return ok(requestId, correlationId, data);
        } catch (Exception e) {
            log.warn("facilityGovernanceSummary failed: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "error", e.getMessage(), "requestId", requestId, "correlationId", correlationId));
        }
    }

    @PostMapping("/onboarding/single-facility")
    public ResponseEntity<Map<String, Object>> onboardSingleFacility(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = workforceGovernanceClient.postJson("/v1/internal/governance/onboarding/single-facility", body);
            return ResponseEntity.status(201).body(okBody(requestId, correlationId, data));
        } catch (Exception e) {
            log.warn("onboardSingleFacility failed: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "error", e.getMessage(), "requestId", requestId, "correlationId", correlationId));
        }
    }

    @PostMapping("/onboarding/multi-facility")
    public ResponseEntity<Map<String, Object>> onboardMultiFacility(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = workforceGovernanceClient.postJson("/v1/internal/governance/onboarding/multi-facility", body);
            return ResponseEntity.status(201).body(okBody(requestId, correlationId, data));
        } catch (Exception e) {
            log.warn("onboardMultiFacility failed: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "error", e.getMessage(), "requestId", requestId, "correlationId", correlationId));
        }
    }

    @GetMapping("/programmes/{id}")
    public ResponseEntity<Map<String, Object>> getProgramme(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String id) {
        try {
            JsonNode data = workforceGovernanceClient.getJson("/v1/internal/governance/programmes/" + id);
            return ok(requestId, correlationId, data);
        } catch (Exception e) {
            log.warn("getProgramme failed: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "error", e.getMessage(), "requestId", requestId, "correlationId", correlationId));
        }
    }

    private static ResponseEntity<Map<String, Object>> ok(String requestId, String correlationId, JsonNode data) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("requestId", requestId);
        body.put("correlationId", correlationId);
        body.put("data", data);
        return ResponseEntity.ok(body);
    }

    private static Map<String, Object> okBody(String requestId, String correlationId, JsonNode data) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("requestId", requestId);
        body.put("correlationId", correlationId);
        body.put("data", data);
        return body;
    }
}
