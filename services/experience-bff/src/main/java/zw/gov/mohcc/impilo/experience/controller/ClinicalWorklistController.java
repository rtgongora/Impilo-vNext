package zw.gov.mohcc.impilo.experience.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.worklist.ClinicalWorklistComposer;
import zw.gov.mohcc.impilo.experience.worklist.WorklistRanking;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Composed clinician worklist across queue, referrals, orders, pharmacy, telemedicine, and tasks.
 *
 * This endpoint intentionally provides a single actionable inbox abstraction for frontend
 * workflow orchestration while upstream services remain domain-siloed.
 *
 * <p>Composition and ranking are delegated to {@link ClinicalWorklistComposer} and
 * {@link WorklistRanking} (Phase E, E1) so the Work Home composition layer (E3/E4) shares
 * this exact logic instead of duplicating it.</p>
 */
@RestController
public class ClinicalWorklistController {

    private final ClinicalWorklistComposer composer;

    public ClinicalWorklistController(ClinicalWorklistComposer composer) {
        this.composer = composer;
    }

    @GetMapping("/internal/v1/clinical-worklist")
    public ResponseEntity<Map<String, Object>> clinicalWorklist(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "facility_id") String facilityId,
            @RequestParam(required = false, name = "assignee_id") String assigneeId,
            @RequestParam(defaultValue = "40", name = "size") int size
    ) {
        int safeSize = Math.min(Math.max(size, 5), 200);
        List<Map<String, Object>> items = composer.composeAll(facilityId, safeSize);

        if (assigneeId != null && !assigneeId.isBlank()) {
            items = items.stream().filter(it -> {
                String candidate = text(it.get("assignee_id"));
                return candidate == null || candidate.isBlank() || assigneeId.equals(candidate);
            }).toList();
        }

        items = WorklistRanking.sort(items);

        if (items.size() > safeSize) {
            items = new ArrayList<>(items.subList(0, safeSize));
        }

        Map<String, Object> summary = WorklistRanking.summarize(items);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", items);
        data.put("summary", summary);

        return ResponseEntity.ok(Map.of(
                "data", data,
                "meta", Map.of(
                        "request_id", requestId,
                        "correlation_id", correlationId,
                        "facility_id", facilityId,
                        "generated_at", Instant.now().toString()
                )
        ));
    }

    @GetMapping("/internal/v1/mobile/provider/clinical-worklist")
    public ResponseEntity<Map<String, Object>> mobileClinicalWorklist(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "facility_id") String facilityId,
            @RequestParam(required = false, name = "assignee_id") String assigneeId,
            @RequestParam(defaultValue = "40", name = "size") int size
    ) {
        return clinicalWorklist(tenantId, requestId, correlationId, facilityId, assigneeId, size);
    }

    private static String text(Object value) {
        if (value == null) return null;
        String s = String.valueOf(value);
        return s.isBlank() ? null : s;
    }
}
