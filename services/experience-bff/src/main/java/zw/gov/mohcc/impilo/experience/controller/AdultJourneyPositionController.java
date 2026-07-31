package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;

import java.util.Map;

@RestController
@RequestMapping("/internal/v1/medicine/journey-position")
public class AdultJourneyPositionController {

    private static final Logger log = LoggerFactory.getLogger(AdultJourneyPositionController.class);

    private final PctServiceClient pctClient;

    public AdultJourneyPositionController(PctServiceClient pctClient) {
        this.pctClient = pctClient;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> current(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "subject_cpid") String subjectCpid,
            @RequestParam(name = "journey_id", required = false) String journeyId,
            @RequestParam(name = "encounter_id", required = false) String encounterId) {
        try {
            JsonNode data = pctClient.getAdultJourneyPosition(subjectCpid, journeyId, encounterId);
            return ResponseEntity.ok(Map.of(
                    "data", data != null ? data : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("PCT journey position read failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", "journey_position_unavailable",
                    "message", "Journey position could not be read. Do not infer pathway progress.",
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> record(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = pctClient.recordAdultJourneyPosition(body);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "data", data != null ? data : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("PCT journey position write failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", "journey_position_write_failed",
                    "message", "Journey position was not saved.",
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }
}
