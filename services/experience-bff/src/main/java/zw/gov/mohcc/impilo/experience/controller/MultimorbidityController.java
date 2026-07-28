package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.ClinicalKnowledgePlatformClient;
import zw.gov.mohcc.impilo.experience.client.OrosServiceClient;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;

import java.util.List;
import java.util.Map;

/**
 * The multimorbidity view (brief.md §9), composed for one patient.
 *
 * <p>Composition is the whole job here. §9's detections are about relationships between sources —
 * a diet conflict between two conditions, a duplicate between two medicines, a repeat between two
 * orders — so somebody has to hold all the sources at once, and that is this layer. The engine in
 * CKP does the reasoning; nothing clinical is decided here.</p>
 *
 * <p><strong>A source that could not be read is omitted, never emptied.</strong> The engine treats a
 * missing key as "not obtained" and an empty array as "there are none", and answers UNDETERMINED for
 * the first and NOT_DETECTED for the second. Defaulting a failed read to {@code []} — the reflex
 * that makes most composition code tidy — would convert every outage into a clean bill of health on
 * the one screen a clinician opens to find what the other screens hid.</p>
 */
@RestController
@RequestMapping("/internal/v1/medicine/multimorbidity")
public class MultimorbidityController {

    private static final Logger log = LoggerFactory.getLogger(MultimorbidityController.class);

    private final ClinicalKnowledgePlatformClient ckp;
    private final PctServiceClient pct;
    private final OrosServiceClient oros;
    private final ObjectMapper objectMapper;

    public MultimorbidityController(ClinicalKnowledgePlatformClient ckp, PctServiceClient pct,
                                    OrosServiceClient oros, ObjectMapper objectMapper) {
        this.ckp = ckp;
        this.pct = pct;
        this.oros = oros;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> assess(
            @RequestParam(name = "subject_cpid") String subjectCpid,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        ObjectNode body = objectMapper.createObjectNode();

        addConditions(body, subjectCpid);
        addMedicines(body, subjectCpid);
        // Appointments, investigations, functional status, patient priorities and the care team are
        // NOT composed yet — the booking, order-history and functional-assessment reads are not wired
        // from this pack. They are omitted rather than sent empty, so the engine reports the
        // detections that depend on them as unanswered and the view says which. This is the honest
        // shape of a partially-built view, and it is visible on screen rather than only in this
        // comment.

        try {
            JsonNode data = ckp.multimorbidityAssess(body);
            return ResponseEntity.ok(Map.of(
                    "data", data, "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("CKP multimorbidity assessment failed for subject={}: {}", subjectCpid, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", "multimorbidity_unavailable",
                    "message", "The multimorbidity view could not be assembled. Nothing was checked — "
                               + "do not read this as an absence of conflicts.",
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    private void addConditions(ObjectNode body, String subjectCpid) {
        try {
            JsonNode problems = pct.listProblems(subjectCpid);
            if (problems == null || !problems.isArray()) {
                log.warn("Problem list unreadable for subject={} — omitted so the engine reports it "
                         + "unanswered rather than reporting no conditions", subjectCpid);
                return;
            }
            ArrayNode conditions = body.putArray("conditions");
            for (JsonNode p : problems) {
                if (isResolved(p.path("clinical_status").asText(p.path("clinicalStatus").asText("")))) {
                    continue;
                }
                ObjectNode c = conditions.addObject();
                c.put("code", p.path("code").asText(""));
                c.put("display", p.path("display").asText(""));
                // Chronicity is not stored on a problem — pct V100 deliberately removed CHRONIC as a
                // category ("a chronicity attribute rather than a kind") and nothing replaced it. We
                // therefore cannot assert it, and asserting it falsely would inflate the burden score.
                c.put("longTerm", false);
            }
        } catch (Exception e) {
            log.warn("Problem list could not be read for subject={}: {} — omitted, not emptied",
                    subjectCpid, e.getMessage());
            body.remove("conditions");
        }
    }

    private void addMedicines(ObjectNode body, String subjectCpid) {
        List<String> medicines = oros.currentMedicationCodes(subjectCpid);
        if (medicines == null) {
            log.warn("Medicine list unavailable for subject={} — omitted so duplication and unsafe "
                     + "combinations report unanswered rather than clear", subjectCpid);
            return;
        }
        ArrayNode array = body.putArray("medicationCodes");
        medicines.forEach(array::add);
    }

    private static boolean isResolved(String clinicalStatus) {
        if (clinicalStatus == null || clinicalStatus.isBlank()) {
            return false;
        }
        return switch (clinicalStatus.toUpperCase(java.util.Locale.ROOT)) {
            case "RESOLVED", "INACTIVE" -> true;
            default -> false;
        };
    }
}
