package zw.gov.mohcc.impilo.costa.api;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.costa.api.dto.financial.*;
import zw.gov.mohcc.impilo.costa.domain.entity.BudgetActualReferenceEntity;
import zw.gov.mohcc.impilo.costa.domain.entity.BudgetCommitmentEntity;
import zw.gov.mohcc.impilo.costa.service.*;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Budget execution surface: commitments, actuals, availability control,
 * variance and forecast. Rides the same {@code /internal/v1/finance/budgets}
 * namespace as the lifecycle controller.
 */
@RestController
@RequestMapping("/internal/v1/finance/budgets")
public class BudgetExecutionController {

    private final BudgetCommitmentService commitmentService;
    private final BudgetActualService actualService;
    private final BudgetAvailabilityService availabilityService;
    private final BudgetVarianceService varianceService;
    private final BudgetForecastService forecastService;

    public BudgetExecutionController(BudgetCommitmentService commitmentService,
                                     BudgetActualService actualService,
                                     BudgetAvailabilityService availabilityService,
                                     BudgetVarianceService varianceService,
                                     BudgetForecastService forecastService) {
        this.commitmentService = commitmentService;
        this.actualService = actualService;
        this.availabilityService = availabilityService;
        this.varianceService = varianceService;
        this.forecastService = forecastService;
    }

    // ----- commitments ------------------------------------------------------

    @PostMapping("/managed/{budgetId}/commitments")
    public ResponseEntity<ApiResponse<Map<String, Object>>> recordCommitment(
            @PathVariable UUID budgetId, @Valid @RequestBody RecordCommitmentRequest body) {
        TrustContext ctx = TrustContextHolder.require();
        BudgetCommitmentEntity c = commitmentService.record(ctx.tenantId(), budgetId,
                new BudgetCommitmentService.NewCommitment(body.budgetLineId(), body.ownerSystem(),
                        body.ownerReference(), body.description(), body.committedAmount(),
                        body.obligatedAmount(), body.expiresAt()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(BudgetJsonMapper.commitment(c), cid(ctx)));
    }

    @GetMapping("/managed/{budgetId}/commitments")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listCommitments(@PathVariable UUID budgetId) {
        TrustContext ctx = TrustContextHolder.require();
        List<Map<String, Object>> rows = commitmentService.list(ctx.tenantId(), budgetId).stream()
                .map(BudgetJsonMapper::commitment).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(rows, cid(ctx)));
    }

    @PostMapping("/commitments/{commitmentId}/liquidate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> liquidate(
            @PathVariable UUID commitmentId, @Valid @RequestBody LiquidateCommitmentRequest body) {
        TrustContext ctx = TrustContextHolder.require();
        BudgetCommitmentEntity c = commitmentService.liquidate(ctx.tenantId(), commitmentId, body.amount());
        return ResponseEntity.ok(ApiResponse.ok(BudgetJsonMapper.commitment(c), cid(ctx)));
    }

    @PostMapping("/commitments/{commitmentId}/cancel")
    public ResponseEntity<ApiResponse<Map<String, Object>>> cancel(@PathVariable UUID commitmentId) {
        TrustContext ctx = TrustContextHolder.require();
        BudgetCommitmentEntity c = commitmentService.cancel(ctx.tenantId(), commitmentId);
        return ResponseEntity.ok(ApiResponse.ok(BudgetJsonMapper.commitment(c), cid(ctx)));
    }

    // ----- actuals ----------------------------------------------------------

    @PostMapping("/managed/{budgetId}/actuals")
    public ResponseEntity<ApiResponse<Map<String, Object>>> postActual(
            @PathVariable UUID budgetId, @Valid @RequestBody PostActualRequest body) {
        TrustContext ctx = TrustContextHolder.require();
        BudgetActualReferenceEntity a = actualService.post(ctx.tenantId(), budgetId,
                new BudgetActualService.NewActual(body.budgetLineId(), body.source(),
                        body.sourceReference(), body.commitmentId(), body.actualAmount(),
                        body.ingestSource()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(BudgetJsonMapper.actual(a), cid(ctx)));
    }

    @GetMapping("/managed/{budgetId}/actuals")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listActuals(@PathVariable UUID budgetId) {
        TrustContext ctx = TrustContextHolder.require();
        List<Map<String, Object>> rows = actualService.list(ctx.tenantId(), budgetId).stream()
                .map(BudgetJsonMapper::actual).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(rows, cid(ctx)));
    }

    // ----- availability control --------------------------------------------

    @PostMapping("/availability/check")
    public ResponseEntity<ApiResponse<Map<String, Object>>> availability(
            @Valid @RequestBody AvailabilityCheckRequest body) {
        TrustContext ctx = TrustContextHolder.require();
        var r = availabilityService.check(ctx.tenantId(), body.budgetLineId(), body.requestedAmount(),
                Boolean.TRUE.equals(body.emergency()), Boolean.TRUE.equals(body.clinicalEmergency()));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("decision", r.decision().name());
        m.put("budgetLineId", r.budgetLineId().toString());
        m.put("allocated", r.allocated());
        m.put("committed", r.committed());
        m.put("actual", r.actual());
        m.put("available", r.available());
        m.put("requested", r.requested());
        m.put("projectedUtilisation", r.projectedUtilisation());
        m.put("emergency", r.emergency());
        m.put("reason", r.reason());
        return ResponseEntity.ok(ApiResponse.ok(m, cid(ctx)));
    }

    // ----- variance ---------------------------------------------------------

    @GetMapping("/managed/{budgetId}/variance")
    public ResponseEntity<ApiResponse<Map<String, Object>>> variance(
            @PathVariable UUID budgetId,
            @RequestParam(name = "as_of", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(
                BudgetJsonMapper.variance(varianceService.compute(ctx.tenantId(), budgetId, asOf)), cid(ctx)));
    }

    @PostMapping("/managed/{budgetId}/variance/snapshot")
    public ResponseEntity<ApiResponse<Map<String, Object>>> varianceSnapshot(
            @PathVariable UUID budgetId,
            @RequestParam(name = "as_of", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
                BudgetJsonMapper.variance(varianceService.snapshot(ctx.tenantId(), budgetId, asOf)), cid(ctx)));
    }

    // ----- forecast ---------------------------------------------------------

    @GetMapping("/managed/{budgetId}/forecast")
    public ResponseEntity<ApiResponse<Map<String, Object>>> forecast(
            @PathVariable UUID budgetId,
            @RequestParam(name = "method", required = false) String method,
            @RequestParam(name = "as_of", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(
                BudgetJsonMapper.forecast(forecastService.compute(ctx.tenantId(), budgetId, method, asOf)), cid(ctx)));
    }

    @PostMapping("/managed/{budgetId}/forecast/snapshot")
    public ResponseEntity<ApiResponse<Map<String, Object>>> forecastSnapshot(
            @PathVariable UUID budgetId,
            @RequestParam(name = "method", required = false) String method,
            @RequestParam(name = "as_of", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
                BudgetJsonMapper.forecast(forecastService.snapshot(ctx.tenantId(), budgetId, method, asOf)), cid(ctx)));
    }

    private static String cid(TrustContext ctx) {
        return ctx.correlationId() != null ? ctx.correlationId().toString() : null;
    }
}
