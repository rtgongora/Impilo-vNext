package zw.gov.mohcc.impilo.learning.api.fundo;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.learning.fundo.FundoReportService;

/** Trainer/supervisor reporting APIs for native Fundo LMS. */
@RestController
@RequestMapping("/internal/v1/learning/fundo/reports")
public class FundoReportController {

    private final FundoReportService reportService;

    public FundoReportController(FundoReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/overview")
    public ResponseEntity<Map<String, Object>> overview() {
        ResponseEntity<Map<String, Object>> forbidden = FundoApiSupport.requireReportAccess();
        if (forbidden != null) return forbidden;
        return FundoApiSupport.dataEnvelope(reportService.overview(null));
    }

    @GetMapping("/course-completions")
    public ResponseEntity<Map<String, Object>> courseCompletions(
            @RequestParam(required = false) String courseId,
            @RequestParam(required = false) String subjectType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer limit) {
        ResponseEntity<Map<String, Object>> forbidden = FundoApiSupport.requireReportAccess();
        if (forbidden != null) return forbidden;
        return FundoApiSupport.dataEnvelope(reportService.courseCompletions(
                null, FundoApiSupport.tryParseUuid(courseId), subjectType, status, limit));
    }

    @GetMapping("/overdue-learning")
    public ResponseEntity<Map<String, Object>> overdueLearning(
            @RequestParam(required = false) String courseId,
            @RequestParam(required = false) String subjectType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer limit) {
        ResponseEntity<Map<String, Object>> forbidden = FundoApiSupport.requireReportAccess();
        if (forbidden != null) return forbidden;
        return FundoApiSupport.dataEnvelope(reportService.overdueLearning(
                null, FundoApiSupport.tryParseUuid(courseId), subjectType, status, limit));
    }

    @GetMapping("/assessment-performance")
    public ResponseEntity<Map<String, Object>> assessmentPerformance(
            @RequestParam(required = false) String courseId,
            @RequestParam(required = false) String subjectType,
            @RequestParam(required = false) Integer limit) {
        ResponseEntity<Map<String, Object>> forbidden = FundoApiSupport.requireReportAccess();
        if (forbidden != null) return forbidden;
        return FundoApiSupport.dataEnvelope(reportService.assessmentPerformance(
                null, FundoApiSupport.tryParseUuid(courseId), subjectType, limit));
    }
}
