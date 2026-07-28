package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.support.PctTimelineRows;

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
                // Falling through would render an empty timeline for what is actually an
                // upstream outage — the same shape a patient with no history produces.
                log.error("PCT getEncounter for timeline failed for encounter={}: {}",
                        encounterId, e.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                        "error", "timeline_unavailable",
                        "message", "The encounter could not be resolved because PCT is "
                                   + "unreachable. Do not treat this as an absence of history.",
                        "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
            }
        }

        if (cpid == null || cpid.isBlank()) {
            return ResponseEntity.ok(Map.of(
                    "data", List.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
        try {
            JsonNode pctData = pctClient.getPatientTimeline(cpid);
            // PCT returns journeys with nested encounters. useTimeline declares TimelineEntryResource[]
            // with attributes.eventType/title/occurredAt, and the timeline page filters on
            // `eventType === "REFERRAL"` — none of which exist in the grouped payload. Derive the
            // events from what PCT actually recorded rather than handing the page a shape it cannot read.
            return ResponseEntity.ok(Map.of(
                    "data", PctTimelineRows.timelineEntries(pctData),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            // An empty 200 here reads as "this patient has no clinical history", which is an
            // affirmative finding and would be a fabricated one. Fail loudly instead.
            log.error("PCT getPatientTimeline failed for patient={}: {}", cpid, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", "timeline_unavailable",
                    "message", "The clinical timeline could not be retrieved. Do not treat this "
                               + "as an absence of history.",
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }
}
