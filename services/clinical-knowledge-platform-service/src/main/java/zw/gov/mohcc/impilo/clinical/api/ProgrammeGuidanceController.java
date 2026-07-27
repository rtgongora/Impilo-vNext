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
 * Governed HIV/TB programme decision support.
 *
 * <p>Advisory and auditable, like the other CKP engines: it interprets the facts a caller supplies
 * (viral load, CD4, WHO stage, treatment month, sputum result) against the versioned DAK content and
 * returns what fired, what could not be assessed, and where each recommendation came from. It never
 * overrides provider judgement, and a result built on unsupplied inputs is marked incomplete rather
 * than reading as an all-clear.</p>
 */
@RestController
@RequestMapping("/internal/v1/clinical/programme")
public class ProgrammeGuidanceController {

    private final ProgrammeGuidanceService guidanceService;

    public ProgrammeGuidanceController(ProgrammeGuidanceService guidanceService) {
        this.guidanceService = guidanceService;
    }

    @PostMapping("/{programme}/evaluate")
    public ResponseEntity<Map<String, Object>> evaluate(
            @PathVariable String programme, @RequestBody Map<String, Object> body) {
        var assessment = guidanceService.evaluate(programme, body);
        return ResponseEntity.ok(Map.of("data", assessment.toMap()));
    }
}
