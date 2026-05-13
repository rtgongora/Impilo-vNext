package zw.gov.mohcc.impilo.learning.api.v11.fundo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.learning.fundo.FundoAssessmentService;
import zw.gov.mohcc.impilo.learning.fundo.FundoCatalogService;
import zw.gov.mohcc.impilo.learning.fundo.FundoCourseStructureService;

/**
 * Native Fundo v1.1 catalogue surface (Phase 5B). Lists published courses,
 * returns course detail / structure, and surfaces published assessments for
 * a course. Standalone-capable: no Moodle / external LMS invocation.
 */
@RestController
@RequestMapping("/internal/v1/learning/v11")
public class FundoCatalogController {

    private final FundoCatalogService catalogService;
    private final FundoCourseStructureService structureService;
    private final FundoAssessmentService assessmentService;

    public FundoCatalogController(
            FundoCatalogService catalogService,
            FundoCourseStructureService structureService,
            FundoAssessmentService assessmentService) {
        this.catalogService = catalogService;
        this.structureService = structureService;
        this.assessmentService = assessmentService;
    }

    @GetMapping("/catalog")
    public ResponseEntity<Map<String, Object>> listCatalog(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) Boolean cpdEligible,
            @RequestParam(required = false) Boolean mandatory,
            @RequestParam(required = false) String language,
            @RequestParam(defaultValue = "25") int limit) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = FundoV11Support.requireTenantOrNull(ctx);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("limit", limit);
        if (tenantId == null) {
            data.put("items", List.of());
            return FundoV11Support.dataEnvelope(data);
        }
        List<Map<String, Object>> items = catalogService.listCatalogue(
                tenantId,
                new FundoCatalogService.CatalogueFilter(status, category, level, cpdEligible, mandatory, language),
                limit);
        data.put("items", items);
        return FundoV11Support.dataEnvelope(data);
    }

    @GetMapping("/catalog/{courseId}")
    public ResponseEntity<Map<String, Object>> getCourse(@PathVariable String courseId) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = FundoV11Support.requireTenantOrNull(ctx);
        UUID cid = FundoV11Support.tryParseUuid(courseId);
        if (tenantId == null || cid == null) {
            return FundoV11Support.notFound("COURSE_NOT_FOUND", "Course not found");
        }
        Optional<Map<String, Object>> course = catalogService.getCourse(tenantId, cid);
        return course
                .map(c -> FundoV11Support.dataEnvelope("course", c))
                .orElseGet(() -> FundoV11Support.notFound("COURSE_NOT_FOUND", "Course not found"));
    }

    @GetMapping("/courses/{courseId}/structure")
    public ResponseEntity<Map<String, Object>> getStructure(@PathVariable String courseId) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = FundoV11Support.requireTenantOrNull(ctx);
        UUID cid = FundoV11Support.tryParseUuid(courseId);
        if (tenantId == null || cid == null) {
            return FundoV11Support.notFound("COURSE_NOT_FOUND", "Course not found");
        }
        return structureService.getStructure(tenantId, cid)
                .map(s -> FundoV11Support.dataEnvelope("structure", s))
                .orElseGet(() -> FundoV11Support.notFound("COURSE_NOT_FOUND", "Course not found"));
    }

    @GetMapping("/courses/{courseId}/assessments")
    public ResponseEntity<Map<String, Object>> listAssessments(@PathVariable String courseId) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = FundoV11Support.requireTenantOrNull(ctx);
        UUID cid = FundoV11Support.tryParseUuid(courseId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("courseId", courseId);
        if (tenantId == null || cid == null) {
            data.put("items", List.of());
            return FundoV11Support.dataEnvelope(data);
        }
        data.put("items", assessmentService.listByCourse(tenantId, cid));
        return FundoV11Support.dataEnvelope(data);
    }
}
