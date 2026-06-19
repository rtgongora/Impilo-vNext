package zw.gov.mohcc.impilo.vashandi.api;

import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.vashandi.core.WorkforceAnalyticsService;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.UUID;

@RestController
@RequestMapping("/v1/internal/vashandi/analytics")
public class VashandiAnalyticsController {

    private final WorkforceAnalyticsService analyticsService;

    public VashandiAnalyticsController(WorkforceAnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/staffing")
    public VashandiDtos.StaffingAnalyticsResponse staffing(
            @RequestParam(value = "facility_id", required = false) UUID facilityId) {
        return analyticsService.staffing(tenantId(), facilityId);
    }

    @GetMapping("/roster-coverage")
    public VashandiDtos.RosterCoverageResponse rosterCoverage(
            @RequestParam(value = "facility_id", required = false) UUID facilityId) {
        return analyticsService.rosterCoverage(tenantId(), facilityId);
    }

    @GetMapping("/attendance")
    public VashandiDtos.AttendanceAnalyticsResponse attendance() {
        return analyticsService.attendance(tenantId());
    }

    @GetMapping("/vacancies")
    public VashandiDtos.VacancyAnalyticsResponse vacancies(
            @RequestParam(value = "facility_id", required = false) UUID facilityId) {
        return analyticsService.vacancies(tenantId(), facilityId);
    }

    @GetMapping("/access-risk")
    public VashandiDtos.AccessRiskAnalyticsResponse accessRisk() {
        return analyticsService.accessRisk(tenantId());
    }

    private UUID tenantId() {
        TrustContext ctx = TrustContextHolder.require();
        return ctx.tenantId();
    }
}
