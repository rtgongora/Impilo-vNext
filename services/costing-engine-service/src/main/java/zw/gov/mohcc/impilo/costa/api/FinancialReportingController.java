package zw.gov.mohcc.impilo.costa.api;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.costa.api.dto.financial.ClosePeriodRequest;
import zw.gov.mohcc.impilo.costa.service.FinancialReportingService;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/finance/reports")
public class FinancialReportingController {

    private final FinancialReportingService reportingService;

    public FinancialReportingController(FinancialReportingService reportingService) {
        this.reportingService = reportingService;
    }

    @GetMapping("/revenue-summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> revenueSummary(
            @RequestParam(name = "facility_id", required = false) UUID facilityId,
            @RequestParam int year,
            @RequestParam int month) {
        var ctx = TrustContextHolder.require();
        Map<String, Object> data = reportingService.generateRevenueSummary(ctx.tenantId(), facilityId, year, month);
        return ResponseEntity.ok(ApiResponse.ok(data, ctx.correlationId().toString()));
    }

    @GetMapping("/claims-expenditure")
    public ResponseEntity<ApiResponse<Map<String, Object>>> claimsExpenditure(
            @RequestParam(name = "payer_id", required = false) String payerId,
            @RequestParam int year,
            @RequestParam int month) {
        var ctx = TrustContextHolder.require();
        Map<String, Object> data = reportingService.generateClaimsExpenditure(ctx.tenantId(), payerId, year, month);
        return ResponseEntity.ok(ApiResponse.ok(data, ctx.correlationId().toString()));
    }

    @GetMapping("/provider-payments")
    public ResponseEntity<ApiResponse<Map<String, Object>>> providerPayments(
            @RequestParam(name = "provider_id") UUID providerId,
            @RequestParam int year,
            @RequestParam int month) {
        var ctx = TrustContextHolder.require();
        Map<String, Object> data = reportingService.generateProviderPaymentSummary(ctx.tenantId(), providerId, year, month);
        return ResponseEntity.ok(ApiResponse.ok(data, ctx.correlationId().toString()));
    }

    @GetMapping("/debt-aging")
    public ResponseEntity<ApiResponse<Map<String, Object>>> debtAging(
            @RequestParam(name = "facility_id", required = false) UUID facilityId) {
        var ctx = TrustContextHolder.require();
        Map<String, Object> data = reportingService.generateDebtAgingReport(ctx.tenantId(), facilityId);
        return ResponseEntity.ok(ApiResponse.ok(data, ctx.correlationId().toString()));
    }

    @GetMapping("/budget-vs-actual")
    public ResponseEntity<ApiResponse<Map<String, Object>>> budgetVsActual(
            @RequestParam(name = "facility_id") UUID facilityId,
            @RequestParam int year) {
        var ctx = TrustContextHolder.require();
        Map<String, Object> data = reportingService.generateBudgetVsActual(ctx.tenantId(), facilityId, year);
        return ResponseEntity.ok(ApiResponse.ok(data, ctx.correlationId().toString()));
    }

    @PostMapping("/close-period")
    public ResponseEntity<ApiResponse<Map<String, Object>>> closePeriod(@Valid @RequestBody ClosePeriodRequest body) {
        var ctx = TrustContextHolder.require();
        String closedBy = ctx.actorId() != null ? ctx.actorId() : "system";
        Map<String, Object> data = reportingService.closePeriod(
                ctx.tenantId(), body.periodType(), body.year(), body.month(), closedBy);
        return ResponseEntity.ok(ApiResponse.ok(data, ctx.correlationId().toString()));
    }
}
