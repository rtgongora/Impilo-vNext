package zw.gov.mohcc.impilo.pct.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.pct.core.clinical.ResultActionService;
import zw.gov.mohcc.impilo.pct.persistence.entity.ResultActionEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * What was done about a diagnostic result (brief.md §11, §25).
 *
 * <p>OROS owns the result and its acknowledgement and is not duplicated here. This records the
 * clinical action the result caused.</p>
 */
@RestController
@RequestMapping("/v1/result-actions")
public class ResultActionController {

    private final ResultActionService service;

    public ResultActionController(ResultActionService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> list(
            @RequestParam(name = "subject_cpid") String subjectCpid) {
        return ResponseEntity.ok(ApiResponse.ok(
                service.listForSubject(subjectCpid).stream().map(ResultActionController::toMap)
                        .collect(Collectors.toList()),
                correlationId()));
    }

    /**
     * Which of the supplied results have been actioned.
     *
     * <p>The caller supplies the result set because PCT does not hold one — OROS does. Answering
     * only about results PCT happens to know would make the unactioned ones invisible, and those are
     * the entire point of the read.</p>
     */
    @PostMapping("/status")
    public ResponseEntity<Map<String, Object>> status(@RequestBody Map<String, Object> body) {
        Object refs = body.get("result_refs") == null ? body.get("resultRefs") : body.get("result_refs");
        List<String> resultRefs = refs instanceof List<?> l
                ? l.stream().filter(java.util.Objects::nonNull).map(String::valueOf).toList()
                : List.of();
        return ResponseEntity.ok(Map.of("data", service.actionStatus(resultRefs)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> record(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(toMap(service.record(body)), correlationId()));
    }

    private static Map<String, Object> toMap(ResultActionEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("action_id", e.getActionId());
        m.put("subject_cpid", e.getSubjectCpid());
        m.put("result_ref", e.getResultRef());
        m.put("order_ref", e.getOrderRef());
        m.put("action", e.getAction());
        m.put("rationale", e.getRationale());
        m.put("problem_id", e.getProblemId());
        m.put("actioned_by", e.getActionedBy());
        m.put("actioned_at", e.getActionedAt());
        return m;
    }

    private static String correlationId() {
        return TrustContextHolder.require().correlationId().toString();
    }
}
