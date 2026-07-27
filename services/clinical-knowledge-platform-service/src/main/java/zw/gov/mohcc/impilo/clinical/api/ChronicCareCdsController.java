package zw.gov.mohcc.impilo.clinical.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.clinical.programme.ProgrammeGuidanceService;

import java.util.Map;

/**
 * Chronic-disease decision support for adult medicine (W4) — cardiovascular risk (WHO HEARTS) and
 * deprescribing / renal-medication safety (WHO PEN).
 *
 * <p>These are not programmes, but they are the same governed tabular content evaluated the same
 * way — against {@code PatientFacts} (eGFR, weight, hepatic impairment) plus caller-supplied facts.
 * Rather than fork a second engine, this delegates to the shared evaluator. Advisory and auditable
 * like the other CKP engines; a result built on unsupplied inputs is reported incomplete, never as
 * an all-clear. Topics: {@code cvd-risk}, {@code deprescribing}.</p>
 */
@RestController
@RequestMapping("/internal/v1/clinical/cds/chronic")
public class ChronicCareCdsController {

    private final ProgrammeGuidanceService guidanceService;

    public ChronicCareCdsController(ProgrammeGuidanceService guidanceService) {
        this.guidanceService = guidanceService;
    }

    @PostMapping("/{topic}/evaluate")
    public ResponseEntity<Map<String, Object>> evaluate(
            @PathVariable String topic, @RequestBody Map<String, Object> facts) {
        var assessment = guidanceService.evaluate(topic, facts);
        return ResponseEntity.ok(Map.of("data", assessment.toMap()));
    }
}
