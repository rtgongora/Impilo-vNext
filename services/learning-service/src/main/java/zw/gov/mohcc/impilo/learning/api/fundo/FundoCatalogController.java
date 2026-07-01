package zw.gov.mohcc.impilo.learning.api.fundo;

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
 * Native Fundo catalogue surface. Lists published courses, returns course
 * detail / structure, and surfaces published assessments for a course.
 */
@RestController
@RequestMapping("/internal/v1/learning/fundo")
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
        UUID tenantId = FundoApiSupport.requireTenantOrNull(ctx);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("limit", limit);
        if (tenantId == null) {
            data.put("items", List.of());
            return FundoApiSupport.dataEnvelope(data);
        }
        List<Map<String, Object>> items = catalogService.listCatalogue(
                tenantId,
                new FundoCatalogService.CatalogueFilter(status, category, level, cpdEligible, mandatory, language),
                limit);
        data.put("items", items);
        return FundoApiSupport.dataEnvelope(data);
    }

    @GetMapping("/catalog/{courseId}")
    public ResponseEntity<Map<String, Object>> getCourse(@PathVariable String courseId) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = FundoApiSupport.requireTenantOrNull(ctx);
        UUID cid = FundoApiSupport.tryParseUuid(courseId);
        if (tenantId == null || cid == null) {
            return FundoApiSupport.notFound("COURSE_NOT_FOUND", "Course not found");
        }
        Optional<Map<String, Object>> course = catalogService.getCourse(tenantId, cid);
        return course
                .map(c -> FundoApiSupport.dataEnvelope("course", c))
                .orElseGet(() -> FundoApiSupport.notFound("COURSE_NOT_FOUND", "Course not found"));
    }

    @GetMapping("/courses/{courseId}/structure")
    public ResponseEntity<Map<String, Object>> getStructure(@PathVariable String courseId) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = FundoApiSupport.requireTenantOrNull(ctx);
        UUID cid = FundoApiSupport.tryParseUuid(courseId);
        if (tenantId == null || cid == null) {
            return FundoApiSupport.notFound("COURSE_NOT_FOUND", "Course not found");
        }
        return structureService.getStructure(tenantId, cid)
                .map(s -> FundoApiSupport.dataEnvelope("structure", s))
                .orElseGet(() -> FundoApiSupport.notFound("COURSE_NOT_FOUND", "Course not found"));
    }

    @GetMapping("/courses/{courseId}/assessments")
    public ResponseEntity<Map<String, Object>> listAssessments(@PathVariable String courseId) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = FundoApiSupport.requireTenantOrNull(ctx);
        UUID cid = FundoApiSupport.tryParseUuid(courseId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("courseId", courseId);
        if (tenantId == null || cid == null) {
            data.put("items", List.of());
            return FundoApiSupport.dataEnvelope(data);
        }
        data.put("items", assessmentService.listByCourse(tenantId, cid));
        return FundoApiSupport.dataEnvelope(data);
    }
}
