package zw.gov.mohcc.impilo.varapi.api.controller;

import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.varapi.core.StudentRegistrationReportService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Student registration reports (NCZ-W1D), served from the service that owns the tables.
 *
 * <p>reporting-service catalogues these by reference. It cannot execute them: its database has no
 * varapi schema and no FDW, which is why five ACTIVE definitions there query varapi and return
 * nothing. A report is where its data is.</p>
 */
@RestController
@RequestMapping("/v1/internal/student-applications/reports")
public class StudentRegistrationReportController {

    private final StudentRegistrationReportService reportService;

    public StudentRegistrationReportController(StudentRegistrationReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/regulator-board")
    public Map<String, Object> regulatorBoard(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestParam Long councilId,
            @RequestParam(defaultValue = "STUDENT_REGISTRATION") String applicationType) {
        return reportService.regulatorBoard(tenantId, councilId, applicationType);
    }

    @GetMapping("/ageing")
    public List<Map<String, Object>> ageing(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestParam Long councilId,
            @RequestParam(defaultValue = "STUDENT_REGISTRATION") String applicationType) {
        return reportService.ageing(tenantId, councilId, applicationType);
    }

    @GetMapping("/returns-by-section")
    public List<Map<String, Object>> returnsBySection(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestParam Long councilId,
            @RequestParam(defaultValue = "STUDENT_REGISTRATION") String applicationType) {
        return reportService.returnsBySection(tenantId, councilId, applicationType);
    }

    @GetMapping("/institution-board")
    public Map<String, Object> institutionBoard(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestParam UUID organisationId) {
        return reportService.institutionBoard(tenantId, organisationId);
    }
}
