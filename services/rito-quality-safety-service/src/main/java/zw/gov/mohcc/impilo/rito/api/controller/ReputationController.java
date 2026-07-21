package zw.gov.mohcc.impilo.rito.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.rito.api.dto.RatingDtos.ManagementViewResponse;
import zw.gov.mohcc.impilo.rito.core.ReputationAggregationService;
import zw.gov.mohcc.impilo.rito.core.ReputationReadService;

import java.util.Map;
import java.util.UUID;

/**
 * Authorised reputation reads + recompute (RW5) — internal, TSHEPO-gated lane. The
 * management view exposes all four domains + moderation counts. The recompute trigger
 * regenerates the derived summary on demand (the scheduled aggregator does it routinely).
 */
@RestController
@RequestMapping("/internal/v1/rito/reputation")
public class ReputationController {

    private final ReputationReadService readService;
    private final ReputationAggregationService aggregationService;

    public ReputationController(ReputationReadService readService,
                               ReputationAggregationService aggregationService) {
        this.readService = readService;
        this.aggregationService = aggregationService;
    }

    @GetMapping("/{providerPublicId}/management")
    public ResponseEntity<ManagementViewResponse> managementView(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable String providerPublicId,
            @RequestParam(required = false) String period) {
        return ResponseEntity.ok(readService.managementView(tenantId, providerPublicId, period));
    }

    @PostMapping("/{providerPublicId}/recompute")
    public ResponseEntity<Map<String, Object>> recompute(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable String providerPublicId,
            @RequestParam(required = false) String period) {
        int summaries = aggregationService.recomputeForProvider(tenantId, providerPublicId, period);
        return ResponseEntity.ok(Map.of("providerPublicId", providerPublicId, "summariesRecomputed", summaries));
    }
}
