package zw.gov.mohcc.impilo.tshepo.api.biometric;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * HTTP API for biometric policy evaluation (Tshepo trust plane).
 *
 * <p>Downstream sovereign services (VITO, VARAPI, BFF) call this before performing
 * enrollment, verification, identification, deduplication-assist, or step-up.</p>
 */
@RestController
@RequestMapping("/v1/biometric-policy")
public class BiometricPolicyController {

    private final BiometricPolicyEvaluationService evaluationService;

    public BiometricPolicyController(BiometricPolicyEvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    @PostMapping("/evaluate")
    public ResponseEntity<BiometricPolicyEvaluationResponse> evaluate(
            @RequestBody BiometricPolicyEvaluateRequest request) {
        return ResponseEntity.ok(evaluationService.evaluate(request));
    }
}
