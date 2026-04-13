package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * Clinical timeline endpoints.
 * GET /internal/v1/timeline?patient_id= — list timeline for patient (paged, desc by occurred_at).
 * GET /internal/v1/timeline?encounter_id= — list timeline for encounter (list, no pagination).
 */
@RestController
@RequestMapping("/internal/v1/timeline")
public class ClinicalTimelineController {

    private static final Logger log = LoggerFactory.getLogger(ClinicalTimelineController.class);

    private final PctServiceClient pctClient;

    public ClinicalTimelineController(PctServiceClient pctClient) {
        this.pctClient = pctClient;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listTimeline(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false, name = "patient_id") String patientId,
            @RequestParam(required = false, name = "encounter_id") String encounterId) {
        String cpid = patientId;
        if ((cpid == null || cpid.isBlank()) && encounterId != null && !encounterId.isBlank()) {
            try {
                long enc = Long.parseLong(encounterId.trim());
                JsonNode encNode = pctClient.getEncounter(enc);
                if (encNode != null && encNode.has("subjectCpid")) {
                    cpid = encNode.get("subjectCpid").asText();
                }
            } catch (NumberFormatException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "encounter_id must be a numeric PCT encounter id when patient_id is omitted");
            } catch (Exception e) {
                log.warn("PCT getEncounter for timeline failed: {}", e.getMessage());
            }
        }

        if (cpid == null || cpid.isBlank()) {
            return ResponseEntity.ok(Map.of(
                    "data", List.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
        try {
            JsonNode pctData = pctClient.getPatientTimeline(cpid);
            return ResponseEntity.ok(Map.of(
                    "data", pctData != null ? pctData : List.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("PCT getPatientTimeline failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "data", List.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }
}
