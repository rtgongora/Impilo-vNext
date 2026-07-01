package zw.gov.mohcc.impilo.tuso.api.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.tuso.api.dto.FacilityImportRowDtos;
import zw.gov.mohcc.impilo.tuso.api.dto.FacilityImportRunDtos;
import zw.gov.mohcc.impilo.tuso.api.dto.FacilityMasterImportDtos;
import zw.gov.mohcc.impilo.tuso.core.FacilityMasterImportService;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityImportRowEntity;

import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/v1/internal/facilities/import")
public class FacilityMasterImportController {

    private static final Logger log = LoggerFactory.getLogger(FacilityMasterImportController.class);

    private final FacilityMasterImportService importService;

    public FacilityMasterImportController(FacilityMasterImportService importService) {
        this.importService = importService;
    }

    @PostMapping("/master-pack")
    public ResponseEntity<ApiResponse<FacilityMasterImportDtos.FacilityMasterImportResponse>> importMasterPack(
            @Valid @RequestBody FacilityMasterImportDtos.FacilityMasterImportRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Facility master pack import dryRun={} records={} correlationId={}",
                request.dryRun(), request.records() != null ? request.records().size() : 0, ctx.correlationId());
        FacilityMasterImportDtos.FacilityMasterImportResponse response = importService.importPack(request);
        return ResponseEntity.ok(ApiResponse.ok(response, ctx.correlationId().toString()));
    }

    @PostMapping("/master-pack/dry-run")
    public ResponseEntity<ApiResponse<FacilityMasterImportDtos.FacilityMasterImportResponse>> dryRunMasterPack(
            @Valid @RequestBody FacilityMasterImportDtos.FacilityMasterImportRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        var dryRunRequest = new FacilityMasterImportDtos.FacilityMasterImportRequest(
                true,
                request.syncNdila(),
                request.reconcileDuplicateCodes(),
                request.records());
        FacilityMasterImportDtos.FacilityMasterImportResponse response = importService.importPack(dryRunRequest);
        return ResponseEntity.ok(ApiResponse.ok(response, ctx.correlationId().toString()));
    }

    /** List persisted facility import runs/batches for the current tenant, most recent first. */
    @GetMapping("/runs")
    public ResponseEntity<ApiResponse<FacilityImportRunDtos.FacilityImportRunListResponse>> listRuns() {
        TrustContext ctx = TrustContextHolder.require();
        List<FacilityImportRunDtos.FacilityImportRunView> runs = importService.listRuns();
        var body = new FacilityImportRunDtos.FacilityImportRunListResponse(runs.size(), runs);
        return ResponseEntity.ok(ApiResponse.ok(body, ctx.correlationId().toString()));
    }

    /** Read a single import run/batch detail by id (tenant-scoped). */
    @GetMapping("/runs/{runId}")
    public ResponseEntity<ApiResponse<FacilityImportRunDtos.FacilityImportRunView>> getRun(
            @PathVariable Long runId) {
        TrustContext ctx = TrustContextHolder.require();
        FacilityImportRunDtos.FacilityImportRunView run = importService.getRun(runId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Facility import run not found: " + runId));
        return ResponseEntity.ok(ApiResponse.ok(run, ctx.correlationId().toString()));
    }

    /** Filtered, paginated persisted rows for a run. {@code status} is an alias for {@code outcome}. */
    @GetMapping("/runs/{runId}/rows")
    public ResponseEntity<ApiResponse<FacilityImportRowDtos.PagedRows>> getRows(
            @PathVariable Long runId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) String decisionStatus,
            @RequestParam(required = false) String duplicateType,
            @RequestParam(required = false) String province,
            @RequestParam(required = false) String district,
            @RequestParam(required = false) String facilityCode,
            @RequestParam(required = false) String facilityName,
            @RequestParam(required = false) Boolean hasAcceptableMissingFields,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        TrustContext ctx = TrustContextHolder.require();
        String effectiveOutcome = outcome != null ? outcome : status;
        var rows = importService.searchRows(runId, effectiveOutcome, decisionStatus, duplicateType, province,
                district, facilityCode, facilityName, hasAcceptableMissingFields, page, size);
        return ResponseEntity.ok(ApiResponse.ok(rows, ctx.correlationId().toString()));
    }

    /** Review buckets (counts + preview) built from persisted rows. */
    @GetMapping("/runs/{runId}/review")
    public ResponseEntity<ApiResponse<FacilityImportRowDtos.ReviewBuckets>> getReview(
            @PathVariable Long runId) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(importService.reviewBuckets(runId), ctx.correlationId().toString()));
    }

    /** Duplicate rows grouped for review; {@code type} defaults to FACILITY_CODE. */
    @GetMapping("/runs/{runId}/duplicates")
    public ResponseEntity<ApiResponse<FacilityImportRowDtos.DuplicateGroupsResponse>> getDuplicates(
            @PathVariable Long runId,
            @RequestParam(defaultValue = FacilityImportRowEntity.DUP_TYPE_CODE) String type) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(importService.duplicateGroups(runId, type), ctx.correlationId().toString()));
    }

    /** Rows excluded for missing facility code. */
    @GetMapping("/runs/{runId}/missing-code")
    public ResponseEntity<ApiResponse<FacilityImportRowDtos.PagedRows>> getMissingCode(
            @PathVariable Long runId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        TrustContext ctx = TrustContextHolder.require();
        var rows = importService.searchRows(runId, FacilityImportRowEntity.EXCLUDED_MISSING_FACILITY_CODE,
                null, null, null, null, null, null, null, page, size);
        return ResponseEntity.ok(ApiResponse.ok(rows, ctx.correlationId().toString()));
    }

    /** Import-eligible rows that carry acceptable-missing fields. */
    @GetMapping("/runs/{runId}/acceptable-missing")
    public ResponseEntity<ApiResponse<FacilityImportRowDtos.PagedRows>> getAcceptableMissing(
            @PathVariable Long runId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        TrustContext ctx = TrustContextHolder.require();
        var rows = importService.searchRows(runId, null, null, null, null, null, null, null, true, page, size);
        return ResponseEntity.ok(ApiResponse.ok(rows, ctx.correlationId().toString()));
    }

    // ---- Review-decision mutations (reviewer identity from trust context) ----

    @PostMapping("/runs/{runId}/rows/{rowId}/supply-code")
    public ResponseEntity<ApiResponse<FacilityImportRowDtos.FacilityImportRowView>> supplyCode(
            @PathVariable Long runId, @PathVariable Long rowId,
            @RequestBody FacilityImportRowDtos.SupplyCodeRequest req) {
        TrustContext ctx = TrustContextHolder.require();
        var row = importService.supplyCode(runId, rowId, req.facilityCode(), req.reason());
        return ResponseEntity.ok(ApiResponse.ok(row, ctx.correlationId().toString()));
    }

    @PostMapping("/runs/{runId}/rows/{rowId}/reject")
    public ResponseEntity<ApiResponse<FacilityImportRowDtos.FacilityImportRowView>> reject(
            @PathVariable Long runId, @PathVariable Long rowId,
            @RequestBody(required = false) FacilityImportRowDtos.ReasonRequest req) {
        TrustContext ctx = TrustContextHolder.require();
        var row = importService.rejectRow(runId, rowId, req != null ? req.reason() : null);
        return ResponseEntity.ok(ApiResponse.ok(row, ctx.correlationId().toString()));
    }

    @PostMapping("/runs/{runId}/rows/{rowId}/skip")
    public ResponseEntity<ApiResponse<FacilityImportRowDtos.FacilityImportRowView>> skip(
            @PathVariable Long runId, @PathVariable Long rowId,
            @RequestBody(required = false) FacilityImportRowDtos.ReasonRequest req) {
        TrustContext ctx = TrustContextHolder.require();
        var row = importService.skipRow(runId, rowId, req != null ? req.reason() : null);
        return ResponseEntity.ok(ApiResponse.ok(row, ctx.correlationId().toString()));
    }

    @PostMapping("/runs/{runId}/rows/{rowId}/match-existing")
    public ResponseEntity<ApiResponse<FacilityImportRowDtos.FacilityImportRowView>> matchExisting(
            @PathVariable Long runId, @PathVariable Long rowId,
            @RequestBody FacilityImportRowDtos.MatchExistingRequest req) {
        TrustContext ctx = TrustContextHolder.require();
        var row = importService.matchExisting(runId, rowId, req.facilityId(), req.reason());
        return ResponseEntity.ok(ApiResponse.ok(row, ctx.correlationId().toString()));
    }

    @PostMapping("/runs/{runId}/rows/{rowId}/resolve-distinct")
    public ResponseEntity<ApiResponse<FacilityImportRowDtos.FacilityImportRowView>> resolveDistinct(
            @PathVariable Long runId, @PathVariable Long rowId,
            @RequestBody FacilityImportRowDtos.ReasonRequest req) {
        TrustContext ctx = TrustContextHolder.require();
        var row = importService.resolveDistinct(runId, rowId, req != null ? req.reason() : null);
        return ResponseEntity.ok(ApiResponse.ok(row, ctx.correlationId().toString()));
    }

    // PATCH is the canonical verb; POST is accepted too so callers on request factories without PATCH
    // support (e.g. the BFF's SimpleClientHttpRequestFactory) can proxy it.
    @RequestMapping(value = "/runs/{runId}/rows/{rowId}/canonical-values",
            method = {RequestMethod.PATCH, RequestMethod.POST})
    public ResponseEntity<ApiResponse<FacilityImportRowDtos.FacilityImportRowView>> updateCanonical(
            @PathVariable Long runId, @PathVariable Long rowId,
            @RequestBody FacilityImportRowDtos.CanonicalValuesRequest req) {
        TrustContext ctx = TrustContextHolder.require();
        var row = importService.updateCanonical(runId, rowId, req);
        return ResponseEntity.ok(ApiResponse.ok(row, ctx.correlationId().toString()));
    }

    @PostMapping("/runs/{runId}/rows/{rowId}/approve")
    public ResponseEntity<ApiResponse<FacilityImportRowDtos.FacilityImportRowView>> approve(
            @PathVariable Long runId, @PathVariable Long rowId) {
        TrustContext ctx = TrustContextHolder.require();
        var row = importService.approveRow(runId, rowId);
        return ResponseEntity.ok(ApiResponse.ok(row, ctx.correlationId().toString()));
    }

    @PostMapping("/runs/{runId}/apply-approved")
    public ResponseEntity<ApiResponse<FacilityImportRowDtos.ApplyApprovedResponse>> applyApproved(
            @PathVariable Long runId,
            @RequestBody(required = false) FacilityImportRowDtos.ApplyApprovedRequest req) {
        TrustContext ctx = TrustContextHolder.require();
        var result = importService.applyApproved(runId, req != null ? req.rowIds() : null);
        return ResponseEntity.ok(ApiResponse.ok(result, ctx.correlationId().toString()));
    }

    @GetMapping("/master-pack/quality-report")
    public ResponseEntity<ApiResponse<Object>> qualityReport() throws Exception {
        TrustContext ctx = TrustContextHolder.require();
        Path report = Path.of("docs/data/facility-master-2024-07-23/generated/facility_data_quality_report.json");
        if (!report.toFile().exists()) {
            return ResponseEntity.ok(ApiResponse.ok(
                    java.util.Map.of("status", "NOT_GENERATED", "message", "Run scripts/data/extract-facility-master-from-pdf.py first"),
                    ctx.correlationId().toString()));
        }
        Object payload = new com.fasterxml.jackson.databind.ObjectMapper().readValue(report.toFile(), Object.class);
        return ResponseEntity.ok(ApiResponse.ok(payload, ctx.correlationId().toString()));
    }
}
