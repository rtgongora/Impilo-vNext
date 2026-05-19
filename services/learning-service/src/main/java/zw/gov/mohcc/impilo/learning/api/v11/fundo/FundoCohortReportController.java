package zw.gov.mohcc.impilo.learning.api.v11.fundo;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.learning.fundo.FundoCohortReportService;

/**
 * Phase 6C — native trainer / supervisor cohort completion report.
 *
 * <p>{@code GET /internal/v1/learning/v11/reports/cohort-completions} returns
 * per-course enrolment + completion + certificate counts for the calling
 * tenant. Optional filters: {@code pathwayId} (restrict to a pathway's
 * member courses) or {@code courseId} (single-course detail). With no
 * filter, the report covers up to 50 published courses (callers should
 * paginate using {@code pathwayId} as a natural cohort grouping for larger
 * tenants).</p>
 */
@RestController
@RequestMapping("/internal/v1/learning/v11")
public class FundoCohortReportController {

    private final FundoCohortReportService reportService;

    public FundoCohortReportController(FundoCohortReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/reports/cohort-completions")
    public ResponseEntity<Map<String, Object>> cohortCompletions(
            @RequestParam(required = false) String pathwayId,
            @RequestParam(required = false) String courseId) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = FundoV11Support.requireTenantOrNull(ctx);
        if (tenantId == null) {
            return FundoV11Support.dataEnvelope(Map.of(
                    "filter", Map.of("pathwayId", pathwayId, "courseId", courseId),
                    "totals", Map.of(
                            "courses", 0, "enrolledCount", 0, "inProgressCount", 0,
                            "completedCount", 0, "cancelledCount", 0, "certificatesIssued", 0),
                    "items", java.util.List.of()));
        }
        UUID pid = FundoV11Support.tryParseUuid(pathwayId);
        UUID cid = FundoV11Support.tryParseUuid(courseId);
        Map<String, Object> data = reportService.cohortCompletions(
                tenantId, new FundoCohortReportService.CohortReportFilter(pid, cid));
        return FundoV11Support.dataEnvelope(data);
    }
}
