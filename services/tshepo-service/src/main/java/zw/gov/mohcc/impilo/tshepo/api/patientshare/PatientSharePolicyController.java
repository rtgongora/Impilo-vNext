package zw.gov.mohcc.impilo.tshepo.api.patientshare;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * HTTP API for patient-mediated record sharing and external provider collaboration (Tshepo trust plane).
 */
@RestController
@RequestMapping("/v1/patient-share-policy")
public class PatientSharePolicyController {

    private final PatientSharePolicyEvaluationService evaluationService;

    public PatientSharePolicyController(PatientSharePolicyEvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    @PostMapping("/evaluate")
    public ResponseEntity<PatientSharePolicyEvaluationResponse> evaluate(
            @RequestBody PatientSharePolicyEvaluateRequest request) {
        return ResponseEntity.ok(evaluationService.evaluate(request));
    }
}
