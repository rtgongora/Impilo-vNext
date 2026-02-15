package zw.gov.mohcc.impilo.ia.api;

import java.math.BigDecimal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.ia.core.Recommendation;
import zw.gov.mohcc.impilo.ia.core.RiskAssessmentService;
import zw.gov.mohcc.impilo.ia.core.RiskLevel;
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
                request.contextType(), request.contextId(),
                request.riskScore(), request.riskLevel(),
                request.factors(), request.recommendation());

        return ResponseEntity.status(201).body(
                ApiResponse.ok(assessment, ctx.correlationId().toString()));
    }

    public record AssessRiskRequest(
            String contextType,
            String contextId,
            BigDecimal riskScore,
            RiskLevel riskLevel,
            String factors,
            Recommendation recommendation
    ) {}
}
