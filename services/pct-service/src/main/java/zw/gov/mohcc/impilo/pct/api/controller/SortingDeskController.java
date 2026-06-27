package zw.gov.mohcc.impilo.pct.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.pct.core.sorting.SortingDeskService;
import zw.gov.mohcc.impilo.pct.core.sorting.VisitTypeCatalog;
import zw.gov.mohcc.impilo.pct.persistence.entity.SortingRecordEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sorting Desk API (decision D5): explicit pre-queue visit-type selection.
 *
 * <p>Authorization is enforced upstream at Envoy ext_authz (policy {@code TRIAGE-RECORD} family, track P).
 * Every sort is audited via the outbox (see {@link SortingDeskService}).</p>
 */
@RestController
@RequestMapping("/v1/sorting-desk")
public class SortingDeskController {

    private final SortingDeskService sortingDeskService;

    public SortingDeskController(SortingDeskService sortingDeskService) {
        this.sortingDeskService = sortingDeskService;
    }

    /** Visit-type catalog (all contexts, or one context via ?context=). No PHI; safe for the sort UI. */
    @GetMapping("/visit-types")
    public ResponseEntity<ApiResponse<Object>> visitTypes(@RequestParam(required = false) String context) {
        Object body = (context == null || context.isBlank())
                ? VisitTypeCatalog.all()
                : VisitTypeCatalog.forContext(context);
        return ResponseEntity.ok(ApiResponse.ok(body, TrustContextHolder.require().correlationId().toString()));
    }

    /** Sort a journey: assign a visit-type within a context (D5). */
    @PostMapping("/journeys/{journeyId}/sort")
    public ResponseEntity<ApiResponse<Map<String, Object>>> sort(
            @PathVariable String journeyId,
            @RequestBody SortBody body) {
        // Authz: enforced at ext_authz via tshepo_authz.policy_rule `clinical-triage-sort-*`
        // (V018) — PROVIDER + CLINICIAN/NURSE, facility-scoped, pinned to /sorting-desk/journeys.
        SortingRecordEntity row = sortingDeskService.sort(
                journeyId, body.context(), body.visitType(), body.arrivalReason());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(toMap(row), TrustContextHolder.require().correlationId().toString()));
    }

    /** The latest sort decision for a journey (the visitType the cockpit feeds to the Cadre Engine). */
    @GetMapping("/journeys/{journeyId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> latest(@PathVariable String journeyId) {
        return sortingDeskService.latestFor(journeyId)
                .map(row -> ResponseEntity.ok(ApiResponse.ok(toMap(row),
                        TrustContextHolder.require().correlationId().toString())))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.ok(Map.of(), TrustContextHolder.require().correlationId().toString())));
    }

    private Map<String, Object> toMap(SortingRecordEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("journey_id", e.getJourneyId());
        m.put("visit_type", e.getVisitType());
        m.put("context", e.getContext());
        m.put("arrival_reason", e.getArrivalReason());
        m.put("fast_track", e.isFastTrack());
        m.put("sorted_by", e.getSortedBy());
        m.put("created_at", e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
        return m;
    }

    public record SortBody(String context, String visitType, String arrivalReason) {}
}
