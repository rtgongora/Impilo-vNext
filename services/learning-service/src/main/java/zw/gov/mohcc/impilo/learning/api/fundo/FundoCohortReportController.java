package zw.gov.mohcc.impilo.learning.api.fundo;

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
 * Native trainer / supervisor cohort completion report.
 *
 * <p>{@code GET /internal/v1/learning/fundo/reports/cohort-completions} returns
 * per-course enrolment + completion + certificate counts for the calling
 * tenant. Optional filters: {@code pathwayId} (restrict to a pathway's
 * member courses) or {@code courseId} (single-course detail). With no
 * filter, the report covers up to 50 published courses (callers should
 * paginate using {@code pathwayId} as a natural cohort grouping for larger
 * tenants).</p>
 */
@RestController
@RequestMapping("/internal/v1/learning/fundo")
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
        UUID tenantId = FundoApiSupport.requireTenantOrNull(ctx);
        if (tenantId == null) {
            return FundoApiSupport.dataEnvelope(Map.of(
                    "filter", Map.of("pathwayId", pathwayId, "courseId", courseId),
                    "totals", Map.of(
                            "courses", 0, "enrolledCount", 0, "inProgressCount", 0,
                            "completedCount", 0, "cancelledCount", 0, "certificatesIssued", 0),
                    "items", java.util.List.of()));
        }
        UUID pid = FundoApiSupport.tryParseUuid(pathwayId);
        UUID cid = FundoApiSupport.tryParseUuid(courseId);
        Map<String, Object> data = reportService.cohortCompletions(
                tenantId, new FundoCohortReportService.CohortReportFilter(pid, cid));
        return FundoApiSupport.dataEnvelope(data);
    }
}
