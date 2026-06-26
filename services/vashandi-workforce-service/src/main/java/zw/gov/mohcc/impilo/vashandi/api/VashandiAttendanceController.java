package zw.gov.mohcc.impilo.vashandi.api;

import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.vashandi.core.AttendanceService;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.AttendanceEventEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/internal/vashandi/attendance")
public class VashandiAttendanceController {

    private final AttendanceService attendanceService;

    public VashandiAttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @GetMapping
    public List<AttendanceEventEntity> list(@RequestParam(value = "workforce_profile_id", required = false) UUID workforceProfileId) {
        return attendanceService.list(tenantId(), workforceProfileId);
    }

    @PostMapping("/check-in")
    public VashandiDtos.AttendanceActionResponse checkIn(@RequestBody VashandiDtos.CheckInRequest request) throws Exception {
        return attendanceService.checkIn(tenantId(), request);
    }

    @PostMapping("/adhoc-check-in")
    public VashandiDtos.AttendanceActionResponse adhocCheckIn(@RequestBody VashandiDtos.AdhocCheckInRequest request)
            throws Exception {
        return attendanceService.adhocCheckIn(tenantId(), request);
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
