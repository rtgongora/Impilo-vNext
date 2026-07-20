package zw.gov.mohcc.impilo.abis.api;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.abis.api.dto.AdjudicationCaseResponse;
import zw.gov.mohcc.impilo.abis.api.dto.AdjudicationRequests.AssignRequest;
import zw.gov.mohcc.impilo.abis.api.dto.AdjudicationRequests.DecisionRequest;
import zw.gov.mohcc.impilo.abis.api.dto.AdjudicationRequests.MergeRequest;
import zw.gov.mohcc.impilo.abis.core.AdjudicationService;
import zw.gov.mohcc.impilo.abis.persistence.entity.AdjudicationCaseEntity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ABIS adjudication console API (W3c). The queue of duplicate-investigation cases opened by
 * enrol-time dedup and 1:N identify — worked by human reviewers.
 *
 * <ul>
 *   <li>{@code GET  /v1/abis/adjudication/cases} — queue (optionally filtered by status).</li>
 *   <li>{@code GET  /v1/abis/adjudication/cases/{id}} — case detail + candidate scores.</li>
 *   <li>{@code POST /v1/abis/adjudication/cases/{id}/assign} — assign a reviewer (→ IN_REVIEW).</li>
 *   <li>{@code POST /v1/abis/adjudication/cases/{id}/decision} — record DISTINCT /
 *       CONFIRMED_DUPLICATE (→ RESOLVED). Requires {@code X-Actor-ID}. Never merges.</li>
 *   <li>{@code POST /v1/abis/adjudication/cases/{id}/merge} — governed merge, DUAL CONTROL:
 *       the {@code X-Actor-ID} here must differ from the decision recorder. Never automatic.</li>
 * </ul>
 */
@RestController
@RequestMapping("/v1/abis/adjudication")
public class AdjudicationController {

    static final String ACTOR_HEADER = "X-Actor-ID";

    private final AdjudicationService adjudicationService;

    public AdjudicationController(AdjudicationService adjudicationService) {
        this.adjudicationService = adjudicationService;
    }

    @GetMapping("/cases")
    public ResponseEntity<Map<String, Object>> listCases(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "50") int size) {
        Page<AdjudicationCaseEntity> result = adjudicationService.listQueue(
                tenantId, status, PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 200)));
        List<AdjudicationCaseResponse> content = result.getContent().stream()
                .map(c -> AdjudicationCaseResponse.of(c, false))
                .toList();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("content", content);
        body.put("page", result.getNumber());
        body.put("size", result.getSize());
        body.put("totalElements", result.getTotalElements());
        body.put("totalPages", result.getTotalPages());
        return ResponseEntity.ok(body);
    }

    @GetMapping("/cases/{id}")
    public ResponseEntity<AdjudicationCaseResponse> getCase(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @PathVariable Long id) {
        AdjudicationCaseEntity kase = adjudicationService.getCase(tenantId, id);
        return ResponseEntity.ok(AdjudicationCaseResponse.of(kase, true));
    }

    @PostMapping("/cases/{id}/assign")
    public ResponseEntity<AdjudicationCaseResponse> assign(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @PathVariable Long id,
            @Valid @RequestBody AssignRequest request) {
        AdjudicationCaseEntity kase = adjudicationService.assignReviewer(tenantId, id, request.reviewer());
        return ResponseEntity.ok(AdjudicationCaseResponse.of(kase, true));
    }

    @PostMapping("/cases/{id}/decision")
    public ResponseEntity<AdjudicationCaseResponse> decide(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestHeader(value = ACTOR_HEADER, required = false) String actorId,
            @PathVariable Long id,
            @Valid @RequestBody DecisionRequest request) {
        AdjudicationCaseEntity kase = adjudicationService.recordDecision(
                tenantId, id, request.decision(), actorId, request.reason());
        return ResponseEntity.ok(AdjudicationCaseResponse.of(kase, true));
    }

    @PostMapping("/cases/{id}/merge")
    public ResponseEntity<AdjudicationCaseResponse> merge(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestHeader(value = ACTOR_HEADER, required = false) String actorId,
            @PathVariable Long id,
            @Valid @RequestBody MergeRequest request) {
        // Dual control is enforced in the service: actorId (second reviewer) must differ
        // from the decision recorder. Merge is NEVER automatic.
        AdjudicationCaseEntity kase = adjudicationService.merge(
                tenantId, id, request.mergeTargetSubjectRef(), actorId);
        return ResponseEntity.ok(AdjudicationCaseResponse.of(kase, true));
    }
}
