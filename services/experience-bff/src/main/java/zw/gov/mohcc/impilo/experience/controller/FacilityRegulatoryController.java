package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;

import java.util.Map;

@RestController
@RequestMapping("/internal/v1/facility-registry")
public class FacilityRegulatoryController {

    private static final Logger log = LoggerFactory.getLogger(FacilityRegulatoryController.class);

    private final TusoServiceClient tusoServiceClient;

    public FacilityRegulatoryController(TusoServiceClient tusoServiceClient) {
        this.tusoServiceClient = tusoServiceClient;
    }

    @GetMapping("/facilities")
    public ResponseEntity<Map<String, Object>> listFacilities(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String regulatoryStatus,
            @RequestParam(required = false) String facilityType,
            @RequestParam(required = false) String province,
            @RequestParam(required = false) String district,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            JsonNode data = tusoServiceClient.listFacilityRegistryFacilities(
                    search, regulatoryStatus, facilityType, province, district, page, size);
            return ok(requestId, correlationId, data);
        } catch (Exception e) {
            log.warn("Facility registry list failed: {}", e.getMessage());
            return ok(requestId, correlationId, Map.of(
                    "items", new Object[0],
                    "page", page,
                    "size", size,
                    "totalElements", 0,
                    "totalPages", 0,
                    "hasNext", false
            ));
        }
    }

    @GetMapping("/facilities/{facilityId}")
    public ResponseEntity<Map<String, Object>> getFacilityProfile(
            @PathVariable String facilityId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            return ok(requestId, correlationId, tusoServiceClient.getFacilityRegulatoryProfile(facilityId));
        } catch (Exception e) {
            log.warn("Facility registry profile failed: {}", e.getMessage());
            return ResponseEntity.status(404).body(Map.of(
                    "error", Map.of("code", "FACILITY_NOT_FOUND", "message", e.getMessage()),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
            ));
        }
    }

    @GetMapping("/facilities/{facilityId}/status-summary")
    public ResponseEntity<Map<String, Object>> getFacilityStatusSummary(
            @PathVariable long facilityId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode data = tusoServiceClient.getFacilityStatusSummary(facilityId);
            return ok(requestId, correlationId, data);
        } catch (Exception e) {
            log.warn("Facility status-summary failed: {}", e.getMessage());
            return ResponseEntity.status(404).body(Map.of(
                    "error", Map.of("code", "FACILITY_NOT_FOUND", "message", e.getMessage()),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
            ));
        }
    }

    @PostMapping("/applications")
    public ResponseEntity<Map<String, Object>> createApplication(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        return created(requestId, correlationId, tusoServiceClient.createFacilityApplication(body));
    }

    @PostMapping("/applications/{applicationId}/submit")
    public ResponseEntity<Map<String, Object>> submitApplication(
            @PathVariable String applicationId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return ok(requestId, correlationId, tusoServiceClient.submitFacilityApplication(applicationId));
    }

    @PostMapping("/applications/{applicationId}/ready-for-inspection")
    public ResponseEntity<Map<String, Object>> markReadyForInspection(
            @PathVariable String applicationId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return ok(requestId, correlationId, tusoServiceClient.markFacilityApplicationReadyForInspection(applicationId));
    }

    @PostMapping("/documents")
    public ResponseEntity<Map<String, Object>> uploadDocument(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        return created(requestId, correlationId, tusoServiceClient.uploadFacilityDocument(body));
    }

    @GetMapping("/checklist-templates")
    public ResponseEntity<Map<String, Object>> listChecklistTemplates(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam String inspectionType,
            @RequestParam(required = false) String facilityType) {
        return ok(requestId, correlationId, tusoServiceClient.listChecklistTemplates(inspectionType, facilityType));
    }

    @PostMapping("/inspections")
    public ResponseEntity<Map<String, Object>> scheduleInspection(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        return created(requestId, correlationId, tusoServiceClient.scheduleFacilityInspection(body));
    }

    @PostMapping("/inspections/{inspectionId}/record")
    public ResponseEntity<Map<String, Object>> recordInspection(
            @PathVariable String inspectionId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        return ok(requestId, correlationId, tusoServiceClient.recordFacilityInspection(inspectionId, body));
    }

    @PostMapping("/compliance-actions/{actionId}")
    public ResponseEntity<Map<String, Object>> updateComplianceAction(
            @PathVariable String actionId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        return ok(requestId, correlationId, tusoServiceClient.updateComplianceAction(actionId, body));
    }

    @PostMapping("/committee-reviews")
    public ResponseEntity<Map<String, Object>> recordCommitteeDecision(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        return created(requestId, correlationId, tusoServiceClient.recordCommitteeDecision(body));
    }

    @PostMapping("/enforcement-cases")
    public ResponseEntity<Map<String, Object>> openEnforcementCase(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        return created(requestId, correlationId, tusoServiceClient.openEnforcementCase(body));
    }

    @GetMapping("/dashboard/summary")
    public ResponseEntity<Map<String, Object>> dashboardSummary(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            return ok(requestId, correlationId, tusoServiceClient.getFacilityRegulatoryDashboard());
        } catch (Exception e) {
            log.warn("Facility registry dashboard failed: {}", e.getMessage());
            return ok(requestId, correlationId, Map.of(
                    "totalFacilities", 0,
                    "activeFacilities", 0,
                    "pendingInspection", 0,
                    "pendingCommitteeReviews", 0,
                    "overdueComplianceActions", 0,
                    "renewalsDueSoon", 0,
                    "openEnforcementCases", 0,
                    "facilitiesByRegulatoryStatus", Map.of()
            ));
        }
    }

    private ResponseEntity<Map<String, Object>> ok(String requestId, String correlationId, Object data) {
        return ResponseEntity.ok(Map.of(
                "data", data,
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
        ));
    }

    private ResponseEntity<Map<String, Object>> created(String requestId, String correlationId, Object data) {
        return ResponseEntity.status(201).body(Map.of(
                "data", data,
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
        ));
    }
}
