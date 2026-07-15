package zw.gov.mohcc.impilo.learning.api.fundo;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.learning.fundo.FundoLearnerJourneyService;

/** Learner dashboard + CPD evidence endpoints for native Fundo. */
@RestController
@RequestMapping("/internal/v1/learning/fundo")
public class FundoLearnerController {

    private final FundoLearnerJourneyService learnerJourneyService;

    public FundoLearnerController(FundoLearnerJourneyService learnerJourneyService) {
        this.learnerJourneyService = learnerJourneyService;
    }

    @GetMapping("/my-learning")
    public ResponseEntity<Map<String, Object>> myLearning(
            @RequestParam String subjectType,
            @RequestParam String subjectId) {
        ResponseEntity<Map<String, Object>> forbidden = FundoApiSupport.requireLearnerAccess();
        if (forbidden != null) return forbidden;
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = FundoApiSupport.requireTenantOrNull(ctx);
        if (tenantId == null) {
            return FundoApiSupport.dataEnvelope(Map.of(
                    "subjectType", subjectType,
                    "subjectId", subjectId,
                    "enrolled", java.util.List.of(),
                    "inProgress", java.util.List.of(),
                    "completed", java.util.List.of(),
                    "overdue", java.util.List.of(),
                    "recommended", java.util.List.of(),
                    "assignedPathways", java.util.List.of(),
                    "certificates", java.util.List.of(),
                    "cpdEligibleCompletions", java.util.List.of()));
        }
        return FundoApiSupport.dataEnvelope(learnerJourneyService.myLearning(
                tenantId, subjectType, subjectId, FundoApiSupport.currentActorType(), FundoApiSupport.currentRoles()));
    }

    @GetMapping("/cpd/evidence")
    public ResponseEntity<Map<String, Object>> cpdEvidence(
            @RequestParam String subjectType,
            @RequestParam String subjectId) {
        ResponseEntity<Map<String, Object>> forbidden = FundoApiSupport.requireLearnerAccess();
        if (forbidden != null) return forbidden;
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = FundoApiSupport.requireTenantOrNull(ctx);
        if (tenantId == null) {
            return FundoApiSupport.dataEnvelope(Map.of("subjectType", subjectType, "subjectId", subjectId, "evidence", java.util.List.of()));
        }
        return FundoApiSupport.dataEnvelope(learnerJourneyService.cpdEvidence(tenantId, subjectType, subjectId));
    }

    @GetMapping("/cpd/eligible-completions")
    public ResponseEntity<Map<String, Object>> cpdEligibleCompletions(
            @RequestParam String subjectType,
            @RequestParam String subjectId) {
        ResponseEntity<Map<String, Object>> forbidden = FundoApiSupport.requireLearnerAccess();
        if (forbidden != null) return forbidden;
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = FundoApiSupport.requireTenantOrNull(ctx);
        if (tenantId == null) {
            return FundoApiSupport.dataEnvelope(Map.of("items", java.util.List.of()));
        }
        Map<String, Object> data = learnerJourneyService.cpdEvidence(tenantId, subjectType, subjectId);
        return FundoApiSupport.dataEnvelope(Map.of("items", data.getOrDefault("evidence", java.util.List.of())));
    }
}
