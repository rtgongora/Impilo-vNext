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
 * Consultation and multidisciplinary decision (brief.md §14), as the experience layer surfaces them.
 *
 * <p>A thin composition over pct, which owns the record. The one thing this layer must not do is
 * tidy the response: every consultation carries {@code owning_service} and a sentence saying that a
 * consultation never transfers care. The failure the record exists to prevent is a reader assuming
 * the answering team took the patient, and dropping the note to slim the payload reintroduces it.</p>
 */
@RestController
@RequestMapping("/internal/v1/consultations")
public class ConsultationsController {

    private static final Logger log = LoggerFactory.getLogger(ConsultationsController.class);

    private final PctServiceClient pct;

    public ConsultationsController(PctServiceClient pct) {
        this.pct = pct;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "patient_id") String patientId) {
        try {
            return ok(pct.listConsultations(patientId), requestId, correlationId);
        } catch (Exception e) {
            log.error("PCT listConsultations failed patient={}: {}", patientId, e.getMessage());
            return error(requestId, correlationId, "consultations_unavailable",
                    "Consultations could not be read. Do not treat this as no consultation having "
                    + "been requested.");
        }
    }

    @GetMapping("/worklist")
    public ResponseEntity<Map<String, Object>> worklist(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam("service") String service) {
        try {
            return ok(pct.consultationWorklist(service), requestId, correlationId);
        } catch (Exception e) {
            log.error("PCT consultationWorklist failed service={}: {}", service, e.getMessage());
            return error(requestId, correlationId, "worklist_unavailable",
                    "The consultation worklist could not be read. Do not treat this as an empty "
                    + "worklist — somebody may be waiting on an answer.");
        }
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> request(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = pct.requestConsultation(body);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("data", data, "meta", meta(requestId, correlationId)));
        } catch (Exception e) {
            log.error("PCT requestConsultation failed: {}", e.getMessage());
            return error(requestId, correlationId, "consultation_not_requested",
                    "The consultation was not sent. Nothing has been recorded and nobody has been "
                    + "asked — do not close the form assuming it was.");
        }
    }

    @PostMapping("/{consultationId}/answer")
    public ResponseEntity<Map<String, Object>> answer(
            @PathVariable String consultationId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            return ok(pct.answerConsultation(consultationId, body), requestId, correlationId);
        } catch (Exception e) {
            log.error("PCT answerConsultation failed id={}: {}", consultationId, e.getMessage());
            return error(requestId, correlationId, "consultation_not_answered",
                    "The advice was not saved. The requesting team is still waiting.");
        }
    }

    @PostMapping("/{consultationId}/decline")
    public ResponseEntity<Map<String, Object>> decline(
            @PathVariable String consultationId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            return ok(pct.declineConsultation(consultationId, body), requestId, correlationId);
        } catch (Exception e) {
            log.error("PCT declineConsultation failed id={}: {}", consultationId, e.getMessage());
            return error(requestId, correlationId, "consultation_not_declined",
                    "The decline was not saved. The requesting team is still waiting.");
        }
    }

    @GetMapping("/mdt")
    public ResponseEntity<Map<String, Object>> mdt(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "patient_id") String patientId) {
        try {
            return ok(pct.listMdtDecisions(patientId), requestId, correlationId);
        } catch (Exception e) {
            log.error("PCT listMdtDecisions failed patient={}: {}", patientId, e.getMessage());
            return error(requestId, correlationId, "mdt_unavailable",
                    "MDT decisions could not be read. Do not treat this as no decision having been "
                    + "made — treatment intent may already be set.");
        }
    }

    @PostMapping("/mdt")
    public ResponseEntity<Map<String, Object>> recordMdt(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = pct.recordMdtDecision(body);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("data", data, "meta", meta(requestId, correlationId)));
        } catch (Exception e) {
            log.error("PCT recordMdtDecision failed: {}", e.getMessage());
            return error(requestId, correlationId, "mdt_not_recorded",
                    "The decision was not saved. Nothing has been recorded.");
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
