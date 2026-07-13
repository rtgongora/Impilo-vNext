package zw.gov.mohcc.impilo.tuso.api.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.tuso.api.dto.FacilityFeeDtos.*;
import zw.gov.mohcc.impilo.tuso.core.FacilityFeeService;
import zw.gov.mohcc.impilo.tuso.core.RegulatoryFeeScheduleService;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityApplicationEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.RegulatoryFeeScheduleEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityApplicationRepository;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Regulatory fee-schedule governance + application fee view (TUSO SoR). Amounts
 * are recorded through governed approval — the platform never invents them; they
 * come from SI 78 of 2017. Sits on the {@code /v1/internal/facility-registry}
 * surface so the experience-bff regulatory proxy reaches it with the admin RBAC seam.
 */
@RestController
@RequestMapping("/v1/internal/facility-registry")
public class FacilityFeeController {

    private static final Logger log = LoggerFactory.getLogger(FacilityFeeController.class);

    private final RegulatoryFeeScheduleService feeScheduleService;
    private final FacilityFeeService facilityFeeService;
    private final FacilityApplicationRepository applicationRepository;

    public FacilityFeeController(RegulatoryFeeScheduleService feeScheduleService,
                                 FacilityFeeService facilityFeeService,
                                 FacilityApplicationRepository applicationRepository) {
        this.feeScheduleService = feeScheduleService;
        this.facilityFeeService = facilityFeeService;
        this.applicationRepository = applicationRepository;
    }

    @GetMapping("/fee-schedules")
    public ResponseEntity<ApiResponse<List<FeeScheduleView>>> list(
            @RequestParam(required = false, defaultValue = "SI_78_2017") String scheduleCode) {
        TrustContext ctx = TrustContextHolder.require();
        List<FeeScheduleView> out = feeScheduleService.listBySchedule(scheduleCode).stream()
                .map(this::toView).toList();
        return ResponseEntity.ok(ApiResponse.ok(out, corr(ctx)));
    }

    @PostMapping("/fee-schedules")
    public ResponseEntity<ApiResponse<FeeScheduleView>> create(@RequestBody CreateFeeScheduleRequest req) {
        TrustContext ctx = TrustContextHolder.require();
        FeeScheduleView out = call(() -> toView(feeScheduleService.create(
                req.scheduleCode(), req.feeCode(), req.appliesToCatalogueCode(), req.appliesToClassCode(),
                req.amount(), req.currency(), req.sourceInstrument(), req.sourceSection(), req.version(), req.notes())));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(out, corr(ctx)));
    }

    @PostMapping("/fee-schedules/{feeId}/approve")
    public ResponseEntity<ApiResponse<FeeScheduleView>> approve(@PathVariable UUID feeId,
                                                               @RequestBody(required = false) ApproveFeeScheduleRequest req) {
        TrustContext ctx = TrustContextHolder.require();
        FeeScheduleView out = call(() -> toView(feeScheduleService.approve(feeId, req == null ? null : req.effectiveFrom())));
        return ResponseEntity.ok(ApiResponse.ok(out, corr(ctx)));
    }

    @GetMapping("/applications/{applicationId}/fee")
    public ResponseEntity<ApiResponse<ApplicationFeeView>> applicationFee(@PathVariable UUID applicationId) {
        TrustContext ctx = TrustContextHolder.require();
        ApplicationFeeView out = call(() -> facilityFeeService.getApplicationFee(requireApplication(applicationId, ctx)));
        return ResponseEntity.ok(ApiResponse.ok(out, corr(ctx)));
    }

    private FacilityApplicationEntity requireApplication(UUID applicationId, TrustContext ctx) {
        FacilityApplicationEntity a = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found: " + applicationId));
        if (ctx.tenantId() != null && a.getTenantId() != null && !ctx.tenantId().equals(a.getTenantId())) {
            throw new SecurityException("Tenant isolation violation");
        }
        return a;
    }

    private static String corr(TrustContext ctx) {
        return ctx.correlationId() != null ? ctx.correlationId().toString() : null;
    }

    private static <T> T call(Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (SecurityException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            throw new ResponseStatusException(
                    msg.toLowerCase().contains("not found") ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST, msg);
        }
    }

    private FeeScheduleView toView(RegulatoryFeeScheduleEntity e) {
        return new FeeScheduleView(e.getFeeId(), e.getScheduleCode(), e.getVersion(), e.getFeeCode(),
                e.getAppliesToCatalogueCode(), e.getAppliesToClassCode(), e.getAmount(), e.getCurrency(),
                e.getSourceInstrument(), e.getSourceSection(), e.getStatus(), e.getEffectiveFrom(),
                e.getApprovedBy(), e.getApprovedAt(), e.getNotes());
    }
}
