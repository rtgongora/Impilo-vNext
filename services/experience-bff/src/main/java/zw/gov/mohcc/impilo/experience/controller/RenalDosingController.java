package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.ClinicalKnowledgePlatformClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Renal dosing awareness for the experience layer (Wave 5.5).
 *
 * <p>Pass-through to CKP where {@link zw.gov.mohcc.impilo.medicine.pharm.RenalDosingAdvisor} lives.
 * A refusal (missing eGFR) is returned as 200 with {@code level: UNKNOWN}, not as an error — the
 * absence of advice must not read as an all-clear.</p>
 */
@RestController
@RequestMapping("/internal/v1/medicine/renal-dosing")
public class RenalDosingController {

    private static final Logger log = LoggerFactory.getLogger(RenalDosingController.class);

    private final ClinicalKnowledgePlatformClient ckp;

    public RenalDosingController(ClinicalKnowledgePlatformClient ckp) {
        this.ckp = ckp;
    }

    @GetMapping("/advise")
    public ResponseEntity<Map<String, Object>> adviseGet(
            @RequestParam(name = "egfr", required = false) Double egfr,
            @RequestParam(name = "drugClass", required = false) String drugClass,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (egfr != null) {
            body.put("egfr", egfr);
        }
        if (drugClass != null) {
            body.put("drugClass", drugClass);
        }
        return advise(body, requestId, correlationId);
    }

    @PostMapping("/advise")
    public ResponseEntity<Map<String, Object>> advisePost(
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return advise(body == null ? Map.of() : body, requestId, correlationId);
    }

    private ResponseEntity<Map<String, Object>> advise(Map<String, Object> body,
                                                     String requestId,
                                                     String correlationId) {
        try {
            JsonNode data = ckp.renalDosingAdvise(body);
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("data", data);
            envelope.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
            return ResponseEntity.ok(envelope);
        } catch (Exception e) {
            log.error("CKP renal dosing advise failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", "renal_dosing_unavailable",
                    "message", "Renal dosing advice could not be evaluated. Do not treat the absence of"
                               + " guidance as safe prescribing.",
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }
}
