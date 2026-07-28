package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.ClinicalKnowledgePlatformClient;
import zw.gov.mohcc.impilo.experience.client.OrosServiceClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Governed adult-medicine decision support, as the experience layer surfaces it (W4–W6): CV risk,
 * deprescribing, medical-procedure indication, geriatrics (ICOPE), mental health (mhGAP),
 * antimicrobial stewardship, palliative care and oncology early diagnosis.
 *
 * <p>A thin composition over the CKP evaluator. Advisory and auditable — it never overrides
 * provider judgement, and a failed evaluation is surfaced as a gateway error, never as an empty
 * result: the absence of alerts must not read as an all-clear when the engine did not run.</p>
 */
@RestController
@RequestMapping("/internal/v1/medicine/cds")
public class MedicineCdsController {

    private static final Logger log = LoggerFactory.getLogger(MedicineCdsController.class);

    private final ClinicalKnowledgePlatformClient ckp;
    private final OrosServiceClient oros;

    public MedicineCdsController(ClinicalKnowledgePlatformClient ckp, OrosServiceClient oros) {
        this.ckp = ckp;
        this.oros = oros;
    }

    @PostMapping("/{topic}/evaluate")
    public ResponseEntity<Map<String, Object>> evaluate(
            @PathVariable String topic,
            @RequestParam(name = "subject_cpid", required = false) String subjectCpid,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> facts) {
        try {
            JsonNode data = ckp.medicineCdsEvaluate(topic, withMedicines(subjectCpid, facts));
            return ResponseEntity.ok(Map.of(
                    "data", data, "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("CKP medicine CDS failed topic={}: {}", topic, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", "medicine_cds_unavailable",
                    "message", "Decision support could not be evaluated. Do not treat the absence of "
                               + "alerts as an all-clear.",
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    /**
     * Compose the patient's current medicines into the facts so the engine can derive medication
     * facts rather than take a clinician's word for them.
     *
     * <p>Composition is exactly what this layer is for: the clinician's form supplies what only a
     * clinician knows, and the BFF supplies what a system already knows. Where OROS could not be
     * asked the key is left out entirely — not set to an empty list — so the engine reports those
     * facts undetermined rather than deriving a false "not on any of these".</p>
     */
    private Map<String, Object> withMedicines(String subjectCpid, Map<String, Object> facts) {
        Map<String, Object> composed = new LinkedHashMap<>(facts == null ? Map.of() : facts);
        if (subjectCpid == null || subjectCpid.isBlank()) {
            return composed;
        }
        List<String> medicines = oros.currentMedicationCodes(subjectCpid);
        if (medicines == null) {
            log.warn("Medicine list unavailable for the CDS evaluation — medication facts will be "
                     + "reported undetermined rather than derived");
            return composed;
        }
        // A caller-supplied list would let the browser decide what the patient is taking; the server
        // is the only party entitled to answer that, so this overwrites rather than defers.
        composed.put("currentMedicationCodes", medicines);
        return composed;
    }
}
