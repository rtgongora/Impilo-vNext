package zw.gov.mohcc.impilo.learning.api.fundo;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.learning.fundo.FundoLearningRecordService;

/**
 * Native Fundo learning record / transcript. Aggregates enrolments, progress,
 * certificates, assessment attempts and CPD-eligible completions for a subject.
 * Fundo exposes CPD eligibility evidence; the canonical CPD ledger remains in
 * varapi-service.
 */
@RestController
@RequestMapping("/internal/v1/learning/fundo")
public class FundoLearningRecordController {

    private final FundoLearningRecordService recordService;

    public FundoLearningRecordController(FundoLearningRecordService recordService) {
        this.recordService = recordService;
    }

    @GetMapping("/subjects/{subjectType}/{subjectId}/record")
    public ResponseEntity<Map<String, Object>> getRecord(
            @PathVariable String subjectType,
            @PathVariable String subjectId) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = FundoApiSupport.requireTenantOrNull(ctx);
        if (tenantId == null) {
            return FundoApiSupport.dataEnvelope("record",
                    Map.of("subjectType", subjectType, "subjectId", subjectId,
                            "enrolments", java.util.List.of(),
                            "certificates", java.util.List.of(),
                            "progressRows", java.util.List.of(),
                            "assessmentAttempts", java.util.List.of(),
                            "cpdEligibleCompletions", java.util.List.of()));
        }
        Map<String, Object> record = recordService.getRecord(tenantId, subjectType, subjectId);
        return FundoApiSupport.dataEnvelope("record", record);
    }
}
