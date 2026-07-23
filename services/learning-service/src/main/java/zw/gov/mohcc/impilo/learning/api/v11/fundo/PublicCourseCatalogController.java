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
import zw.gov.mohcc.impilo.learning.fundo.FundoCatalogService;
import zw.gov.mohcc.impilo.learning.persistence.repository.CourseRepository;

/**
 * Public (R0, anonymous — no-auth) Fundo course catalogue lane.
 *
 * <p>Follows the {@code Public*Controller} rule: allow-listed fields only, NO
 * personalization, NO PII, no internal identifiers (id/tenant/space/version/
 * audit), and no assessed-learning / enrolment / progress surfaces. Only the
 * PUBLISHED catalogue and basic descriptive fields are exposed. Standalone —
 * never invokes Moodle or any external LMS.</p>
 *
 * <p>The public lane is pre-tenant: learning content is tenant-scoped and the
 * citizen-facing catalogue is anchored to the seeded care-plane tenant
 * {@code 00000000-0000-4000-8000-000000000001} (the same tenant the seeded
 * Fundo catalogue is loaded under). We supply it directly rather than reading
 * {@code RequestContext.tenantId()} because the anonymous lane carries no
 * tenant header.</p>
 *
 * <ul>
 *   <li>GET /v1/public/learning/catalog          — PUBLISHED courses (?category= &amp;level= &amp;language= &amp;limit=)</li>
 *   <li>GET /v1/public/learning/catalog/{code}    — one PUBLISHED course by code or id (404 on miss / non-published)</li>
 * </ul>
 */
@RestController
@RequestMapping("/v1/public/learning")
public class PublicCourseCatalogController {

    /** Seeded care-plane tenant that owns the citizen-facing Fundo catalogue. */
    static final UUID PUBLIC_TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");

    private static final String PUBLISHED = "PUBLISHED";

    /** Fields safe to expose on the anonymous lane (drops id/tenant/space/version/audit). */
    private static final List<String> ALLOWED_FIELDS = List.of(
            "code", "title", "description", "category", "level", "language",
            "estimatedDurationMinutes", "mandatory", "cpdEligible", "cpdPoints", "status");

    private final FundoCatalogService catalogService;
    private final CourseRepository courseRepository;

    public PublicCourseCatalogController(FundoCatalogService catalogService, CourseRepository courseRepository) {
        this.catalogService = catalogService;
        this.courseRepository = courseRepository;
    }

    @GetMapping("/catalog")
    public ResponseEntity<Map<String, Object>> listCatalog(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String language,
            @RequestParam(defaultValue = "25") int limit) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("limit", limit);
        List<Map<String, Object>> items = List.of();
        try {
            // Same read FundoCatalogController.listCatalog uses; status forced to PUBLISHED.
            List<Map<String, Object>> rows = catalogService.listCatalogue(
                    PUBLIC_TENANT,
                    new FundoCatalogService.CatalogueFilter(PUBLISHED, category, level, null, null, language),
                    limit);
            items = rows.stream().map(PublicCourseCatalogController::publicView).toList();
        } catch (RuntimeException ex) {
            items = List.of();
        }
        data.put("items", items);
        return dataEnvelope(data);
    }

    @GetMapping("/catalog/{code}")
    public ResponseEntity<Map<String, Object>> getCourse(@PathVariable String code) {
        try {
            Optional<Map<String, Object>> view = resolvePublished(code);
            return view
                    .map(PublicCourseCatalogController::publicView)
                    .map(PublicCourseCatalogController::courseEnvelope)
                    .orElseGet(PublicCourseCatalogController::notFound);
        } catch (RuntimeException ex) {
            return notFound();
        }
    }

    /**
     * Resolves a single PUBLISHED course by id (when the path parses as a UUID)
     * or by code, matching how the catalogue detail read is keyed. Returns the
     * raw catalogue view only when its status is PUBLISHED.
     */
    private Optional<Map<String, Object>> resolvePublished(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        Optional<Map<String, Object>> view;
        UUID asId = FundoV11Support.tryParseUuid(code);
        if (asId != null) {
            view = catalogService.getCourse(PUBLIC_TENANT, asId);
        } else {
            view = courseRepository.findByTenantIdAndCode(PUBLIC_TENANT, code)
                    .map(FundoCatalogService::toView);
        }
        return view.filter(m -> PUBLISHED.equals(m.get("status")));
    }

    /** Projects a full catalogue view down to the anonymous-lane allow-list. */
    private static Map<String, Object> publicView(Map<String, Object> source) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String field : ALLOWED_FIELDS) {
            out.put(field, source.get(field));
        }
        return out;
    }

    private static ResponseEntity<Map<String, Object>> dataEnvelope(Map<String, Object> data) {
        return ResponseEntity.ok(Map.of("data", data));
    }

    private static ResponseEntity<Map<String, Object>> courseEnvelope(Map<String, Object> course) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("course", course);
        return ResponseEntity.ok(Map.of("data", data));
    }

    private static ResponseEntity<Map<String, Object>> notFound() {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", "COURSE_NOT_FOUND");
        error.put("message", "Course not found");
        return ResponseEntity.status(404).body(Map.of("error", error));
    }
}
