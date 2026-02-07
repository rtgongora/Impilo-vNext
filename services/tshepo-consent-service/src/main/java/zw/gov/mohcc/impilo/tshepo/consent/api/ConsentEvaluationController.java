package zw.gov.mohcc.impilo.tshepo.consent.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.ConsentDecision;
import zw.gov.mohcc.impilo.tshepo.consent.service.ConsentEvaluationService;

import java.util.UUID;

/**
 * Consent evaluation endpoint called by tshepo-authz-service on every clinical access decision.
 *
 * <p>This endpoint is the enforcement hook — consent without enforcement is rejected by design.
 * It evaluates all active consent directives and returns a PERMIT or DENY decision
 * with obligations that the authz service must enforce.</p>
 *
 * <p>This endpoint is open (no JWT required) because it is called internally
 * by the authz service. It is protected by network-level isolation (Envoy mesh).</p>
 */
@RestController
@RequestMapping("/v1/consent")
public class ConsentEvaluationController {

    private final ConsentEvaluationService evaluationService;

    public ConsentEvaluationController(ConsentEvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    /**
     * Evaluates consent for a given access request.
     *
     * @param tenantId   the tenant UUID
     * @param actorId    the actor/grantee requesting access
     * @param subjectRef the patient CPID/HealthID reference
     * @param purpose    the purpose of use (e.g., TREATMENT, PAYMENT, RESEARCH)
     * @param scope      the requested scope (e.g., read, write, clinical-data)
     * @return the consent decision (PERMIT or DENY with obligations)
     */
    @GetMapping("/evaluate")
    public ResponseEntity<ApiResponse<ConsentDecision>> evaluate(
            @RequestParam UUID tenantId,
            @RequestParam String actorId,
            @RequestParam String subjectRef,
            @RequestParam String purpose,
            @RequestParam String scope) {

        ConsentDecision decision = evaluationService.evaluate(
                tenantId, actorId, subjectRef, purpose, scope);

        String correlationId = resolveCorrelationId();
        return ResponseEntity.ok(ApiResponse.ok(decision, correlationId));
    }

    private String resolveCorrelationId() {
        var ctx = TrustContextHolder.get();
        if (ctx != null && ctx.correlationId() != null) {
            return ctx.correlationId().toString();
        }
        return UUID.randomUUID().toString();
    }
}
