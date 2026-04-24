package zw.gov.mohcc.impilo.msikaflow.api.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.msikaflow.api.TrustHeaderExtractor;
import zw.gov.mohcc.impilo.msikaflow.api.dto.ApiResponse;
import zw.gov.mohcc.impilo.msikaflow.api.dto.LogisticsDtos;
import zw.gov.mohcc.impilo.msikaflow.core.LogisticsService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/logistics")
public class LogisticsController {

    private final LogisticsService logisticsService;

    public LogisticsController(LogisticsService logisticsService) {
        this.logisticsService = logisticsService;
    }

    @PostMapping("/plans")
    public ResponseEntity<ApiResponse<LogisticsDtos.DeliveryPlanView>> createPlan(
            @Valid @RequestBody LogisticsDtos.CreateDeliveryPlanRequest req,
            HttpServletRequest httpReq) {
        String correlationId = TrustHeaderExtractor.correlationId(httpReq);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(logisticsService.createPlan(req), correlationId));
    }

    @GetMapping("/plans/{planId}")
    public ResponseEntity<ApiResponse<LogisticsDtos.DeliveryPlanView>> getPlan(@PathVariable String planId, HttpServletRequest httpReq) {
        String correlationId = TrustHeaderExtractor.correlationId(httpReq);
        return ResponseEntity.ok(ApiResponse.ok(logisticsService.getPlan(planId), correlationId));
    }

    @GetMapping("/plans")
    public ResponseEntity<ApiResponse<List<LogisticsDtos.DeliveryPlanView>>> listPlans(
            @RequestParam String fulfillmentId, HttpServletRequest httpReq) {
        String correlationId = TrustHeaderExtractor.correlationId(httpReq);
        return ResponseEntity.ok(ApiResponse.ok(logisticsService.listForFulfillment(fulfillmentId), correlationId));
    }

    @PatchMapping("/plans/{planId}")
    public ResponseEntity<ApiResponse<LogisticsDtos.DeliveryPlanView>> updatePlan(
            @PathVariable String planId,
            @RequestBody LogisticsDtos.UpdatePlanRequest req,
            HttpServletRequest httpReq
    ) {
        String correlationId = TrustHeaderExtractor.correlationId(httpReq);
        return ResponseEntity.ok(ApiResponse.ok(logisticsService.updatePlan(planId, req), correlationId));
    }

    @PatchMapping("/legs/{legId}")
    public ResponseEntity<ApiResponse<LogisticsDtos.DeliveryLegView>> updateLeg(
            @PathVariable String legId,
            @RequestBody LogisticsDtos.UpdateLegRequest req,
            HttpServletRequest httpReq
    ) {
        String correlationId = TrustHeaderExtractor.correlationId(httpReq);
        return ResponseEntity.ok(ApiResponse.ok(logisticsService.updateLeg(legId, req), correlationId));
    }

    // ── Providers / assets ─────────────────────────────────────────────────

    @PostMapping("/providers")
    public ResponseEntity<ApiResponse<LogisticsDtos.ProviderView>> createProvider(
            @Valid @RequestBody LogisticsDtos.CreateProviderRequest req,
            HttpServletRequest httpReq
    ) {
        UUID tenantId = TrustHeaderExtractor.tenantId(httpReq);
        String correlationId = TrustHeaderExtractor.correlationId(httpReq);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(logisticsService.createProvider(tenantId, req), correlationId));
    }

    @GetMapping("/providers")
    public ResponseEntity<ApiResponse<List<LogisticsDtos.ProviderView>>> listProviders(
            HttpServletRequest httpReq,
            @RequestParam(defaultValue = "true") boolean activeOnly
    ) {
        UUID tenantId = TrustHeaderExtractor.tenantId(httpReq);
        String correlationId = TrustHeaderExtractor.correlationId(httpReq);
        return ResponseEntity.ok(ApiResponse.ok(logisticsService.listProviders(tenantId, activeOnly), correlationId));
    }

    @PostMapping("/assets")
    public ResponseEntity<ApiResponse<LogisticsDtos.AssetView>> createAsset(
            @Valid @RequestBody LogisticsDtos.CreateAssetRequest req,
            HttpServletRequest httpReq
    ) {
        String correlationId = TrustHeaderExtractor.correlationId(httpReq);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(logisticsService.createAsset(req), correlationId));
    }

    @GetMapping("/assets")
    public ResponseEntity<ApiResponse<List<LogisticsDtos.AssetView>>> listAssets(
            @RequestParam String providerId,
            @RequestParam(defaultValue = "true") boolean activeOnly,
            HttpServletRequest httpReq
    ) {
        String correlationId = TrustHeaderExtractor.correlationId(httpReq);
        return ResponseEntity.ok(ApiResponse.ok(logisticsService.listAssets(providerId, activeOnly), correlationId));
    }

    // ── Missions / handoffs / custody ──────────────────────────────────────

    @PostMapping("/missions")
    public ResponseEntity<ApiResponse<LogisticsDtos.MissionView>> createMission(
            @Valid @RequestBody LogisticsDtos.CreateMissionRequest req,
            HttpServletRequest httpReq
    ) {
        String correlationId = TrustHeaderExtractor.correlationId(httpReq);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(logisticsService.createMission(req), correlationId));
    }

    @GetMapping("/missions")
    public ResponseEntity<ApiResponse<List<LogisticsDtos.MissionView>>> listMissions(
            @RequestParam String legId,
            HttpServletRequest httpReq
    ) {
        String correlationId = TrustHeaderExtractor.correlationId(httpReq);
        return ResponseEntity.ok(ApiResponse.ok(logisticsService.listMissions(legId), correlationId));
    }

    @PostMapping("/handoffs")
    public ResponseEntity<ApiResponse<LogisticsDtos.HandoffView>> recordHandoff(
            @Valid @RequestBody LogisticsDtos.RecordHandoffRequest req,
            HttpServletRequest httpReq
    ) {
        String correlationId = TrustHeaderExtractor.correlationId(httpReq);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(logisticsService.recordHandoff(req), correlationId));
    }

    @GetMapping("/handoffs")
    public ResponseEntity<ApiResponse<List<LogisticsDtos.HandoffView>>> listHandoffs(
            @RequestParam(required = false) String fulfillmentId,
            @RequestParam(required = false) String legId,
            HttpServletRequest httpReq
    ) {
        String correlationId = TrustHeaderExtractor.correlationId(httpReq);
        return ResponseEntity.ok(ApiResponse.ok(logisticsService.listHandoffs(fulfillmentId, legId), correlationId));
    }

    @PostMapping("/custody")
    public ResponseEntity<ApiResponse<LogisticsDtos.CustodyView>> createCustodyRecord(
            @Valid @RequestBody LogisticsDtos.CreateCustodyRequest req,
            HttpServletRequest httpReq
    ) {
        String correlationId = TrustHeaderExtractor.correlationId(httpReq);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(logisticsService.createCustody(req), correlationId));
    }

    @GetMapping("/custody")
    public ResponseEntity<ApiResponse<List<LogisticsDtos.CustodyView>>> listCustody(
            @RequestParam String fulfillmentId,
            HttpServletRequest httpReq
    ) {
        String correlationId = TrustHeaderExtractor.correlationId(httpReq);
        return ResponseEntity.ok(ApiResponse.ok(logisticsService.listCustody(fulfillmentId), correlationId));
    }

    @PostMapping("/exceptions")
    public ResponseEntity<ApiResponse<LogisticsDtos.DeliveryExceptionView>> raiseException(
            @Valid @RequestBody LogisticsDtos.RaiseExceptionRequest req,
            HttpServletRequest httpReq) {
        String correlationId = TrustHeaderExtractor.correlationId(httpReq);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(logisticsService.raiseException(req), correlationId));
    }

    @GetMapping("/exceptions")
    public ResponseEntity<ApiResponse<List<LogisticsDtos.DeliveryExceptionView>>> listExceptions(
            @RequestParam(required = false) String planId,
            @RequestParam(required = false) String status,
            HttpServletRequest httpReq) {
        String correlationId = TrustHeaderExtractor.correlationId(httpReq);
        return ResponseEntity.ok(ApiResponse.ok(logisticsService.listExceptions(planId, status), correlationId));
    }

    @PostMapping("/proofs")
    public ResponseEntity<ApiResponse<LogisticsDtos.ProofView>> recordProof(
            @Valid @RequestBody LogisticsDtos.RecordProofRequest req,
            HttpServletRequest httpReq) {
        String correlationId = TrustHeaderExtractor.correlationId(httpReq);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(logisticsService.recordProof(req), correlationId));
    }

    @GetMapping("/proofs")
    public ResponseEntity<ApiResponse<List<LogisticsDtos.ProofView>>> listProofs(
            @RequestParam(required = false) String planId,
            @RequestParam(required = false) String handoffId,
            HttpServletRequest httpReq) {
        String correlationId = TrustHeaderExtractor.correlationId(httpReq);
        return ResponseEntity.ok(ApiResponse.ok(logisticsService.listProofs(planId, handoffId), correlationId));
    }
}

