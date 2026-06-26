package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.PatientSafetyServiceClient;
import zw.gov.mohcc.impilo.experience.client.PharmacyServiceClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * BFF surface for pharmacovigilance. Proxies the patient-safety-service SoR (reports, cases, MCAZ
 * workbench, investigations, config) and composes a report-prefill view (recently dispensed
 * medicines from pharmacy-service + external VigiMobile links + honest adapter posture). The
 * patient-safety-service remains the system of record; this layer only orchestrates.
 */
@RestController
@RequestMapping("/internal/v1/patient-safety")
public class PatientSafetyController {

    private static final Logger log = LoggerFactory.getLogger(PatientSafetyController.class);

    private final PatientSafetyServiceClient client;
    private final PharmacyServiceClient pharmacy;

    public PatientSafetyController(PatientSafetyServiceClient client, PharmacyServiceClient pharmacy) {
        this.client = client;
        this.pharmacy = pharmacy;
    }

    private Map<String, Object> envelope(JsonNode data, String requestId, String correlationId) {
        return Map.of(
                "data", data != null ? data : Map.of(),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId));
    }

    // ---- reports ------------------------------------------------------------

    @GetMapping("/reports")
    public ResponseEntity<Map<String, Object>> listReports(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "reporter_actor_id", required = false) String reporterActorId,
            @RequestParam(value = "facility_id", required = false) String facilityId) {
        return ResponseEntity.ok(envelope(
                client.listReports(status, reporterActorId, facilityId), requestId, correlationId));
    }

    @GetMapping("/reports/{id}")
    public ResponseEntity<Map<String, Object>> getReport(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        JsonNode data = client.getReport(id);
        if (data == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(envelope(data, requestId, correlationId));
    }

    @PostMapping("/reports")
    public ResponseEntity<Map<String, Object>> createReport(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(201).body(envelope(client.createReport(body), requestId, correlationId));
    }

    @PatchMapping("/reports/{id}")
    public ResponseEntity<Map<String, Object>> updateReport(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(envelope(client.updateReport(id, body), requestId, correlationId));
    }

    @PostMapping("/reports/{id}/products")
    public ResponseEntity<Map<String, Object>> addProduct(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(201).body(envelope(client.addProduct(id, body), requestId, correlationId));
    }

    @PostMapping("/reports/{id}/events")
    public ResponseEntity<Map<String, Object>> addEvent(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(201).body(envelope(client.addEvent(id, body), requestId, correlationId));
    }

    @PostMapping("/reports/{id}/attachments")
    public ResponseEntity<Map<String, Object>> addAttachment(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(201).body(envelope(client.addAttachment(id, body), requestId, correlationId));
    }

    @PostMapping("/reports/{id}/submit")
    public ResponseEntity<Map<String, Object>> submitReport(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return ResponseEntity.ok(envelope(client.submitReport(id), requestId, correlationId));
    }

    // ---- cases --------------------------------------------------------------

    @GetMapping("/cases")
    public ResponseEntity<Map<String, Object>> listCases(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "priority", required = false) String priority) {
        return ResponseEntity.ok(envelope(client.listCases(status, priority), requestId, correlationId));
    }

    @GetMapping("/cases/{id}")
    public ResponseEntity<Map<String, Object>> getCase(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        JsonNode data = client.getCase(id);
        if (data == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(envelope(data, requestId, correlationId));
    }

    @PostMapping("/cases/{id}/triage")
    public ResponseEntity<Map<String, Object>> triage(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(envelope(client.triage(id, body), requestId, correlationId));
    }

    @PostMapping("/cases/{id}/review-actions")
    public ResponseEntity<Map<String, Object>> reviewAction(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(201).body(envelope(client.reviewAction(id, body), requestId, correlationId));
    }

    @PostMapping("/cases/{id}/follow-up")
    public ResponseEntity<Map<String, Object>> requestFollowUp(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(201).body(envelope(client.requestFollowUp(id, body), requestId, correlationId));
    }

    @PostMapping("/cases/{id}/follow-up/{followUpId}/response")
    public ResponseEntity<Map<String, Object>> followUpResponse(
            @PathVariable String id,
            @PathVariable String followUpId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(201).body(
                envelope(client.followUpResponse(id, followUpId, body), requestId, correlationId));
    }

    @PostMapping("/cases/{id}/vigiflow-manual-entry")
    public ResponseEntity<Map<String, Object>> vigiflowManualEntry(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(201).body(
                envelope(client.vigiflowManualEntry(id, body), requestId, correlationId));
    }

    @PostMapping("/cases/{id}/mark-export-ready")
    public ResponseEntity<Map<String, Object>> markExportReady(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return ResponseEntity.status(201).body(envelope(client.markExportReady(id), requestId, correlationId));
    }

    @PostMapping("/cases/{id}/close")
    public ResponseEntity<Map<String, Object>> closeCase(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(envelope(client.closeCase(id, body), requestId, correlationId));
    }

    // ---- MCAZ workbench -----------------------------------------------------

    @GetMapping("/mcaz/workbench")
    public ResponseEntity<Map<String, Object>> workbench(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return ResponseEntity.ok(envelope(client.mcazWorkbench(), requestId, correlationId));
    }

    @GetMapping("/mcaz/incoming-queue")
    public ResponseEntity<Map<String, Object>> incomingQueue(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return ResponseEntity.ok(envelope(client.incomingQueue(), requestId, correlationId));
    }

    @GetMapping("/mcaz/priority-queue")
    public ResponseEntity<Map<String, Object>> priorityQueue(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return ResponseEntity.ok(envelope(client.priorityQueue(), requestId, correlationId));
    }

    // ---- investigations -----------------------------------------------------

    @PostMapping("/cases/{caseId}/investigation")
    public ResponseEntity<Map<String, Object>> openInvestigation(
            @PathVariable String caseId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(201).body(
                envelope(client.openInvestigation(caseId, body), requestId, correlationId));
    }

    @GetMapping("/cases/{caseId}/investigation")
    public ResponseEntity<Map<String, Object>> getInvestigation(
            @PathVariable String caseId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        JsonNode data = client.getInvestigation(caseId);
        if (data == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(envelope(data, requestId, correlationId));
    }

    @PatchMapping("/investigations/{investigationId}")
    public ResponseEntity<Map<String, Object>> updateInvestigation(
            @PathVariable String investigationId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(envelope(client.updateInvestigation(investigationId, body), requestId, correlationId));
    }

    // ---- config + prefill ---------------------------------------------------

    @GetMapping("/config/vigimobile-links")
    public ResponseEntity<Map<String, Object>> vigiMobileLinks(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return ResponseEntity.ok(envelope(client.vigiMobileLinks(), requestId, correlationId));
    }

    @GetMapping("/config/adapters")
    public ResponseEntity<Map<String, Object>> adapters(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return ResponseEntity.ok(envelope(client.adapters(), requestId, correlationId));
    }

    /**
     * Composes a report-prefill view: recently dispensed medicines for the patient (candidate suspect
     * drugs, from pharmacy-service), the external VigiMobile link-outs and the honest adapter posture.
     * Each source is best-effort; a downstream miss yields a partial prefill rather than a hard error.
     */
    @GetMapping("/prefill")
    public ResponseEntity<Map<String, Object>> prefill(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(value = "cpid", required = false) String cpid) {
        Map<String, Object> prefill = new LinkedHashMap<>();
        if (cpid != null && !cpid.isBlank()) {
            try {
                prefill.put("recent_dispenses", pharmacy.getPatientDispenseOrders(cpid));
            } catch (Exception e) {
                log.warn("Patient-safety prefill: pharmacy dispenses unavailable for cpid: {}", e.getMessage());
                prefill.put("recent_dispenses", null);
                prefill.put("recent_dispenses_status", "unavailable");
            }
        }
        try {
            prefill.put("vigimobile_links", client.vigiMobileLinks());
        } catch (Exception e) {
            log.warn("Patient-safety prefill: vigimobile links unavailable: {}", e.getMessage());
        }
        try {
            prefill.put("adapters", client.adapters());
        } catch (Exception e) {
            log.warn("Patient-safety prefill: adapter status unavailable: {}", e.getMessage());
        }
        return ResponseEntity.ok(envelope(
                new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(prefill), requestId, correlationId));
    }
}
