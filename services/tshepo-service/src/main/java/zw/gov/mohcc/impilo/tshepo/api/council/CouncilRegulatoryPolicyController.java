package zw.gov.mohcc.impilo.tshepo.api.council;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP policy evaluation for council-regulated registry workflows (called by Varapi).
 */
@RestController
@RequestMapping("/v1/council-regulatory")
public class CouncilRegulatoryPolicyController {

    private final CouncilRegulatoryEvaluationService evaluationService;

    public CouncilRegulatoryPolicyController(CouncilRegulatoryEvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    @PostMapping("/evaluate")
    public ResponseEntity<CouncilRegulatoryEvaluateResponse> evaluate(
            @RequestBody CouncilRegulatoryEvaluateRequest request) {
        return ResponseEntity.ok(evaluationService.evaluate(request));
    }
}
