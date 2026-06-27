package zw.gov.mohcc.impilo.ia.api;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.ia.core.RiskAssessmentService;
import zw.gov.mohcc.impilo.ia.persistence.entity.RiskAssessmentEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

@RestController
@RequestMapping("/internal/v1/risk")
public class RiskAssessmentController {

    private final RiskAssessmentService riskAssessmentService;

    public RiskAssessmentController(RiskAssessmentService riskAssessmentService) {
        this.riskAssessmentService = riskAssessmentService;
    }

    @PostMapping("/assess")
    public ResponseEntity<ApiResponse<RiskAssessmentEntity>> assess(
            @RequestBody AssessRiskRequest request) {
        TrustContext ctx = TrustContextHolder.require();

        RiskAssessmentEntity assessment = riskAssessmentService.assess(
                ctx.tenantId(), ctx.actorId(), ctx.correlationId(),
                request.contextType(), request.contextId(), request.factors());

        return ResponseEntity.status(201).body(
                ApiResponse.ok(assessment, ctx.correlationId().toString()));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<RiskAssessmentEntity>>> list() {
        TrustContext ctx = TrustContextHolder.require();
        List<RiskAssessmentEntity> assessments = riskAssessmentService.listAssessments(ctx.tenantId());
        return ResponseEntity.ok(ApiResponse.ok(assessments, ctx.correlationId().toString()));
    }

    /**
     * The caller reports only the observed risk signals + context. Score, level and
     * recommendation are computed server-side and are NOT accepted from the client.
     */
    public record AssessRiskRequest(
            String contextType,
            String contextId,
            List<String> factors
    ) {}
}
