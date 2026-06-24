package zw.gov.mohcc.impilo.vashandi.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.vashandi.core.AttendanceHoursService;
import zw.gov.mohcc.impilo.vashandi.core.AttendanceService;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.AttendanceEventEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/v1/internal/vashandi/attendance")
public class VashandiAttendanceController {

    private final AttendanceService attendanceService;
    private final AttendanceHoursService hoursService;

    public VashandiAttendanceController(AttendanceService attendanceService,
                                        AttendanceHoursService hoursService) {
        this.attendanceService = attendanceService;
        this.hoursService = hoursService;
    }

    /**
     * Derived worked-hours + overtime for a worker over a calendar month, resolved from the
     * real check-in/out events. Payroll consumes this (via provider-worker-id) instead of a
     * separately-entered attendance table. 404 when no workforce profile maps the identity —
     * so the caller surfaces the unmapped worker rather than silently assuming zero overtime.
     */
    @GetMapping("/hours-summary")
    public ResponseEntity<AttendanceHoursService.HoursSummary> hoursSummary(
            @RequestParam(value = "provider_worker_id", required = false) String providerWorkerId,
            @RequestParam(value = "health_id", required = false) String healthId,
            @RequestParam int year,
            @RequestParam int month) {
        UUID tenant = tenantId();
        Optional<AttendanceHoursService.HoursSummary> summary;
        if (providerWorkerId != null && !providerWorkerId.isBlank()) {
            summary = hoursService.summarizeMonthByProviderWorkerId(tenant, providerWorkerId, year, month);
        } else if (healthId != null && !healthId.isBlank()) {
            summary = hoursService.summarizeMonthByHealthId(tenant, healthId, year, month);
        } else {
            return ResponseEntity.badRequest().build();
        }
        return summary.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping
    public List<AttendanceEventEntity> list(@RequestParam(value = "workforce_profile_id", required = false) UUID workforceProfileId) {
        return attendanceService.list(tenantId(), workforceProfileId);
    }

    /** Attendance events for a worker (by provider-worker-id) in a calendar month — ERP-HR view. */
    @GetMapping("/events")
    public List<AttendanceEventEntity> eventsByProvider(
            @RequestParam(value = "provider_worker_id") String providerWorkerId,
            @RequestParam int year,
            @RequestParam int month) {
        return hoursService.eventsByProviderWorkerId(tenantId(), providerWorkerId, year, month);
    }

    @PostMapping("/check-in")
    public VashandiDtos.AttendanceActionResponse checkIn(@RequestBody VashandiDtos.CheckInRequest request) throws Exception {
        return attendanceService.checkIn(tenantId(), request);
    }

    @PostMapping("/check-out")
    public VashandiDtos.AttendanceActionResponse checkOut(@RequestBody VashandiDtos.CheckOutRequest request) throws Exception {
        return attendanceService.checkOut(tenantId(), request);
    }

    @PostMapping("/supervisor-confirm")
    public VashandiDtos.AttendanceActionResponse supervisorConfirm(@RequestBody VashandiDtos.SupervisorConfirmRequest request)
            throws Exception {
        return attendanceService.supervisorConfirm(tenantId(), request);
    }

    private UUID tenantId() {
        TrustContext ctx = TrustContextHolder.require();
        return ctx.tenantId();
    }
}
