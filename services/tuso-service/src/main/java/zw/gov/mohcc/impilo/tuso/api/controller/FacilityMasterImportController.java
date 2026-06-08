package zw.gov.mohcc.impilo.tuso.api.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.tuso.api.dto.FacilityMasterImportDtos;
import zw.gov.mohcc.impilo.tuso.core.FacilityMasterImportService;

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
