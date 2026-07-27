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
 * Governed adult-medicine decision support, evaluated as versioned tabular content against
 * {@code PatientFacts} plus caller-supplied facts. One endpoint, many topics — all sharing the
 * engine the programmes use, so there is no forked evaluator.
 *
 * <p>Topics (W4–W6): {@code cvd-risk}, {@code deprescribing} (chronic disease); {@code
 * procedure-indication} (medical procedures); {@code icope} (geriatrics), {@code mhgap} (mental
 * health), {@code antimicrobial-stewardship}, {@code palliative}, {@code oncology}. Each maps to a
 * governed content pack registered under its WHO family in the traceability matrix.</p>
 *
 * <p>Advisory and auditable; never overrides provider judgement. A result built on unsupplied
 * inputs is reported incomplete, never as an all-clear. An unknown topic yields an explicit
 * "no governed content" result rather than an empty one.</p>
 */
@RestController
@RequestMapping("/internal/v1/clinical/cds")
public class MedicineCdsController {

    private final ProgrammeGuidanceService guidanceService;

    public MedicineCdsController(ProgrammeGuidanceService guidanceService) {
        this.guidanceService = guidanceService;
    }

    @PostMapping("/{topic}/evaluate")
    public ResponseEntity<Map<String, Object>> evaluate(
            @PathVariable String topic, @RequestBody Map<String, Object> facts) {
        var assessment = guidanceService.evaluate(topic, facts);
        return ResponseEntity.ok(Map.of("data", assessment.toMap()));
    }
}
