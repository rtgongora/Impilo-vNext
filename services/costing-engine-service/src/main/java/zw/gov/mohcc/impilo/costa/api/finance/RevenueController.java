package zw.gov.mohcc.impilo.costa.api.finance;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.costa.domain.entity.RevenueEntryEntity;
import zw.gov.mohcc.impilo.costa.service.RevenueService;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/finance/revenue")
public class RevenueController {

    private final RevenueService revenueService;

    public RevenueController(RevenueService revenueService) {
        this.revenueService = revenueService;
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> summary(
            @RequestParam("facility_id") UUID facilityId,
            @RequestParam("year") int year,
            @RequestParam("month") int month) {
        var ctx = TrustContextHolder.require();
        return ResponseEntity.ok(revenueService.revenueSummary(ctx.tenantId(), facilityId, year, month));
    }

    @GetMapping("/by-department")
    public ResponseEntity<List<RevenueEntryEntity>> byDepartment(
            @RequestParam("facility_id") UUID facilityId,
            @RequestParam("year") int year,
            @RequestParam("month") int month,
            @RequestParam(value = "department_id", required = false) String departmentId) {
        var ctx = TrustContextHolder.require();
        return ResponseEntity.ok(revenueService.queryByFacilityDepartmentPeriod(
                ctx.tenantId(), facilityId, departmentId, year, month));
    }
}
