package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Structured physical examinations (brief.md §7), as the experience layer surfaces them.
 *
 * <p>A thin composition over pct, which owns the record. The one thing this layer must not do is
 * tidy the response: the read carries a {@code coverage} block naming the regions that were recorded
 * as not examined and those nobody considered at all. A findings list on its own shows only what was
 * looked at — absence has no row — so dropping coverage would turn a partial examination into one
 * that reads as complete.</p>
 */
@RestController
@RequestMapping("/internal/v1/examinations")
public class ExaminationsController {

    private static final Logger log = LoggerFactory.getLogger(ExaminationsController.class);

    private final PctServiceClient pct;

    public ExaminationsController(PctServiceClient pct) {
        this.pct = pct;
    }

    @GetMapping("/vocabulary")
    public ResponseEntity<Map<String, Object>> vocabulary(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            return ok(pct.examinationVocabulary(), requestId, correlationId);
        } catch (Exception e) {
            log.error("PCT examinationVocabulary failed: {}", e.getMessage());
            return error(requestId, correlationId, "examination_vocabulary_unavailable",
                    "The examination vocabulary could not be read, so the form cannot be rendered "
                    + "safely. It is not offered with a guessed set of states.");
        }
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "patient_id") String patientId) {
        try {
            return ok(pct.listExaminations(patientId), requestId, correlationId);
        } catch (Exception e) {
            log.error("PCT listExaminations failed for patient={}: {}", patientId, e.getMessage());
            return error(requestId, correlationId, "examinations_unavailable",
                    "Examinations could not be retrieved. Do not treat this as the patient never "
                    + "having been examined.");
        }
    }

    @GetMapping("/{examinationId}")
    public ResponseEntity<Map<String, Object>> get(
            @PathVariable String examinationId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            return ok(pct.getExamination(examinationId), requestId, correlationId);
        } catch (Exception e) {
            log.error("PCT getExamination failed id={}: {}", examinationId, e.getMessage());
            return error(requestId, correlationId, "examination_unavailable",
                    "The examination could not be read.");
        }
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> record(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = pct.recordExamination(body);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "data", data, "meta", meta(requestId, correlationId)));
        } catch (Exception e) {
            log.error("PCT recordExamination failed: {}", e.getMessage());
            return error(requestId, correlationId, "examination_not_recorded",
                    "The examination was not saved. Nothing has been recorded — do not close the "
                    + "form assuming it was.");
        }
    }

    private static ResponseEntity<Map<String, Object>> ok(JsonNode data, String requestId, String correlationId) {
        return ResponseEntity.ok(Map.of("data", data, "meta", meta(requestId, correlationId)));
    }

    private static ResponseEntity<Map<String, Object>> error(String requestId, String correlationId,
                                                             String code, String message) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "error", code, "message", message, "meta", meta(requestId, correlationId)));
    }

    private static Map<String, Object> meta(String requestId, String correlationId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("request_id", requestId);
        m.put("correlation_id", correlationId);
        return m;
    }
}
