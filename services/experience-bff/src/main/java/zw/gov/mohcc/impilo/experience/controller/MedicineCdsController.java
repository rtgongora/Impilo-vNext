package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.ClinicalKnowledgePlatformClient;

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

    public MedicineCdsController(ClinicalKnowledgePlatformClient ckp) {
        this.ckp = ckp;
    }

    @PostMapping("/{topic}/evaluate")
    public ResponseEntity<Map<String, Object>> evaluate(
            @PathVariable String topic,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> facts) {
        try {
            JsonNode data = ckp.medicineCdsEvaluate(topic, facts);
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
}
