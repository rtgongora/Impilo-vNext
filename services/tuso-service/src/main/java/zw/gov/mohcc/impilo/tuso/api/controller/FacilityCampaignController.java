package zw.gov.mohcc.impilo.tuso.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.tuso.core.FacilityCampaignService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Facility onboarding campaigns (FCV-W6): group a slice of the estate, invite it, watch progress. */
@RestController
@RequestMapping("/v1/internal/facility-registry/campaigns")
public class FacilityCampaignController {

    private final FacilityCampaignService campaignService;

    public FacilityCampaignController(FacilityCampaignService campaignService) {
        this.campaignService = campaignService;
    }

    public record CreateRequest(String name, String description, String province, String district,
                                String facilityType, Integer limit) {}
    public record EscalateRequest(UUID facilityUuid, String note) {}

    @PostMapping
    public ResponseEntity<ApiResponse<FacilityCampaignService.CampaignView>> create(
            @RequestBody CreateRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(campaignService.create(request.name(), request.description(),
                        request.province(), request.district(), request.facilityType(),
                        request.limit() == null ? 500 : request.limit()),
                ctx.correlationId().toString()));
    }

    @PostMapping("/{campaignId}/invite")
    public ResponseEntity<ApiResponse<Map<String, Object>>> invite(@PathVariable UUID campaignId) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(
                Map.of("invited", campaignService.markInvited(campaignId)), ctx.correlationId().toString()));
    }

    @PostMapping("/{campaignId}/escalate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> escalate(
            @PathVariable UUID campaignId, @RequestBody EscalateRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        campaignService.escalate(campaignId, request.facilityUuid(), request.note());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("escalated", true), ctx.correlationId().toString()));
    }

    /** Per-facility progress, derived live from the claim / submission / decision rails. */
    @GetMapping("/{campaignId}/progress")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> progress(@PathVariable UUID campaignId) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(
                campaignService.progress(campaignId), ctx.correlationId().toString()));
    }

    @GetMapping("/{campaignId}/summary")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> summary(@PathVariable UUID campaignId) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(
                campaignService.summary(campaignId), ctx.correlationId().toString()));
    }
}
