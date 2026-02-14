package zw.gov.mohcc.impilo.rules.api;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.rules.api.dto.EvaluateRequest;
import zw.gov.mohcc.impilo.rules.api.dto.EvaluateResponse;
import zw.gov.mohcc.impilo.rules.service.EvaluationService;

@RestController
@RequestMapping("/internal/v1/evaluate")
public class EvaluationController {

    private final EvaluationService evaluationService;

    public EvaluationController(EvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    @PostMapping
    public ResponseEntity<EvaluateResponse> evaluate(@Valid @RequestBody EvaluateRequest request) {
        RequestContext ctx = RequestContextHolder.require();
        EvaluateResponse response = evaluationService.evaluate(request, ctx);
        return ResponseEntity.ok(response);
    }
}
