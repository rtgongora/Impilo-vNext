package zw.gov.mohcc.impilo.msika.api.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.msika.api.dto.GovernanceDtos;
import zw.gov.mohcc.impilo.msika.core.GovernanceService;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.List;

@RestController
@RequestMapping("/v1/governance")
public class GovernanceController {

    private final GovernanceService governanceService;

    public GovernanceController(GovernanceService governanceService) {
        this.governanceService = governanceService;
    }

    @PostMapping("/records")
    @PreAuthorize("hasAnyRole('CATALOG_ADMIN','CATALOG_REVIEWER','REGULATORY_VIEWER','SYSTEM_ADMIN','DEVELOPER')")
    public ResponseEntity<ApiResponse<GovernanceDtos.GovernanceRecordView>> create(
            @Valid @RequestBody GovernanceDtos.CreateGovernanceRecordRequest req) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(governanceService.create(req), correlationId));
    }

    @PostMapping("/records/{recordId}/review")
    @PreAuthorize("hasAnyRole('CATALOG_REVIEWER','CATALOG_ADMIN','SYSTEM_ADMIN','DEVELOPER')")
    public ResponseEntity<ApiResponse<GovernanceDtos.GovernanceRecordView>> review(
            @PathVariable String recordId,
            @Valid @RequestBody GovernanceDtos.ReviewActionRequest req) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(governanceService.review(recordId, req), correlationId));
    }

    @GetMapping("/targets/{targetType}/{targetId}")
    @PreAuthorize("hasAnyRole('CATALOG_ADMIN','CATALOG_REVIEWER','MARKETPLACE_OPERATOR','REGULATORY_VIEWER','SYSTEM_ADMIN','DEVELOPER')")
    public ResponseEntity<ApiResponse<List<GovernanceDtos.GovernanceRecordView>>> listForTarget(
            @PathVariable String targetType, @PathVariable String targetId) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(governanceService.listForTarget(targetType, targetId), correlationId));
    }

    @GetMapping("/queue")
    @PreAuthorize("hasAnyRole('CATALOG_ADMIN','CATALOG_REVIEWER','MARKETPLACE_OPERATOR','SYSTEM_ADMIN','DEVELOPER')")
    public ResponseEntity<ApiResponse<List<GovernanceDtos.GovernanceRecordView>>> queue(
            @RequestParam(required = false) String status) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(governanceService.queue(status), correlationId));
    }
}

