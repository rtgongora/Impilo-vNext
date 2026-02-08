package zw.gov.mohcc.impilo.mushex.api;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.mushex.api.dto.ReconImportLine;
import zw.gov.mohcc.impilo.mushex.api.dto.ReconMatchRequest;
import zw.gov.mohcc.impilo.mushex.service.ReconciliationService;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.shared.response.PagedResponse;

import java.util.List;

@RestController
@RequestMapping("/mushex/v1/recon")
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    public ReconciliationController(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @PostMapping("/import-statement")
    public ResponseEntity<ApiResponse<Object>> importStatement(
            @RequestBody List<ReconImportLine> lines) {
        var ctx = TrustContextHolder.require();
        String correlationId = ctx.correlationId().toString();

        Object result = reconciliationService.importStatement(ctx.tenantId(), lines);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(result, correlationId));
    }

    @GetMapping("/unmatched")
    public ResponseEntity<ApiResponse<PagedResponse<Object>>> getUnmatched(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var ctx = TrustContextHolder.require();
        String correlationId = ctx.correlationId().toString();

        Pageable pageable = PageRequest.of(page, size);
        Page<Object> result = reconciliationService.getUnmatched(ctx.tenantId(), pageable);

        PagedResponse<Object> paged = PagedResponse.of(
                result.getContent(), page, size, result.getTotalElements());

        return ResponseEntity.ok(ApiResponse.ok(paged, correlationId));
    }

    @PostMapping("/match")
    public ResponseEntity<ApiResponse<Object>> matchEntry(
            @RequestParam String reconId,
            @Valid @RequestBody ReconMatchRequest request) {
        var ctx = TrustContextHolder.require();
        String correlationId = ctx.correlationId().toString();

        Object result = reconciliationService.matchEntry(reconId, request.intentId());

        return ResponseEntity.ok(ApiResponse.ok(result, correlationId));
    }
}
