package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * BFF proxy for PCT V117 clerking extensions. Failed reads ≠ empty clerking.
 * Safeguarding stays on /internal/v1/clerking/safeguarding — never social-history.
 */
@RestController
@RequestMapping("/internal/v1/clerking")
public class ClerkingExtensionController {

    private static final Logger log = LoggerFactory.getLogger(ClerkingExtensionController.class);

    private final PctServiceClient pctClient;

    public ClerkingExtensionController(PctServiceClient pctClient) {
        this.pctClient = pctClient;
    }

    private ResponseEntity<Map<String, Object>> get(
            String resource, String subjectCpid, String requestId, String correlationId,
            Supplier<JsonNode> call) {
        try {
            JsonNode data = call.get();
            return ResponseEntity.ok(Map.of(
                    "data", data != null ? data : List.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("PCT clerking {} failed for subject={}: {}", resource, subjectCpid, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", resource + "_unavailable",
                    "message", resource + " could not be retrieved. Do not treat this as an absence of findings.",
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    private ResponseEntity<Map<String, Object>> post(
            String resource, String requestId, String correlationId, Supplier<JsonNode> call) {
        try {
            JsonNode data = call.get();
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "data", data != null ? data : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("PCT clerking {} write failed: {}", resource, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", resource + "_write_failed",
                    "message", "The " + resource + " entry was not saved.",
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    @GetMapping("/presenting-concerns")
    public ResponseEntity<Map<String, Object>> listPresenting(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "subject_cpid") String subjectCpid) {
        return get("presenting_concerns", subjectCpid, requestId, correlationId,
                () -> pctClient.listClerking("presenting-concerns", subjectCpid));
    }

    @PostMapping("/presenting-concerns")
    public ResponseEntity<Map<String, Object>> recordPresenting(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        return post("presenting_concerns", requestId, correlationId,
                () -> pctClient.recordClerking("presenting-concerns", body));
    }

    @GetMapping("/systems-reviews")
    public ResponseEntity<Map<String, Object>> listRos(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "subject_cpid") String subjectCpid) {
        return get("systems_reviews", subjectCpid, requestId, correlationId,
                () -> pctClient.listClerking("systems-reviews", subjectCpid));
    }

    @PostMapping("/systems-reviews")
    public ResponseEntity<Map<String, Object>> recordRos(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        return post("systems_reviews", requestId, correlationId,
                () -> pctClient.recordClerking("systems-reviews", body));
    }

    @GetMapping("/prior-icu")
    public ResponseEntity<Map<String, Object>> listIcu(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "subject_cpid") String subjectCpid) {
        return get("prior_icu", subjectCpid, requestId, correlationId,
                () -> pctClient.listClerking("prior-icu", subjectCpid));
    }

    @PostMapping("/prior-icu")
    public ResponseEntity<Map<String, Object>> recordIcu(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        return post("prior_icu", requestId, correlationId,
                () -> pctClient.recordClerking("prior-icu", body));
    }

    @GetMapping("/infection-exposures")
    public ResponseEntity<Map<String, Object>> listExposure(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "subject_cpid") String subjectCpid) {
        return get("infection_exposures", subjectCpid, requestId, correlationId,
                () -> pctClient.listClerking("infection-exposures", subjectCpid));
    }

    @PostMapping("/infection-exposures")
    public ResponseEntity<Map<String, Object>> recordExposure(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        return post("infection_exposures", requestId, correlationId,
                () -> pctClient.recordClerking("infection-exposures", body));
    }

    @GetMapping("/cognition-mood")
    public ResponseEntity<Map<String, Object>> listCognition(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "subject_cpid") String subjectCpid) {
        return get("cognition_mood", subjectCpid, requestId, correlationId,
                () -> pctClient.listClerking("cognition-mood", subjectCpid));
    }

    @PostMapping("/cognition-mood")
    public ResponseEntity<Map<String, Object>> recordCognition(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        return post("cognition_mood", requestId, correlationId,
                () -> pctClient.recordClerking("cognition-mood", body));
    }

    @GetMapping("/genetic-risks")
    public ResponseEntity<Map<String, Object>> listGenetic(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "subject_cpid") String subjectCpid) {
        return get("genetic_risks", subjectCpid, requestId, correlationId,
                () -> pctClient.listClerking("genetic-risks", subjectCpid));
    }

    @PostMapping("/genetic-risks")
    public ResponseEntity<Map<String, Object>> recordGenetic(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        return post("genetic_risks", requestId, correlationId,
                () -> pctClient.recordClerking("genetic-risks", body));
    }

    @GetMapping("/safeguarding")
    public ResponseEntity<Map<String, Object>> listSafeguarding(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "subject_cpid") String subjectCpid) {
        return get("safeguarding", subjectCpid, requestId, correlationId,
                () -> pctClient.listClerking("safeguarding", subjectCpid));
    }

    @PostMapping("/safeguarding")
    public ResponseEntity<Map<String, Object>> recordSafeguarding(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        return post("safeguarding", requestId, correlationId,
                () -> pctClient.recordClerking("safeguarding", body));
    }
}
