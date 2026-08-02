package zw.gov.mohcc.impilo.learning.fundo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.learning.persistence.entity.CourseEntity;
import zw.gov.mohcc.impilo.learning.persistence.repository.CourseRepository;

/**
 * Native Fundo catalogue service (Phase 5C). Lists and reads
 * {@link CourseEntity} rows with filter support. Standalone-capable —
 * never invokes Moodle or any external LMS.
 */
@Service
public class FundoCatalogService {

    private final CourseRepository courseRepository;

    public FundoCatalogService(CourseRepository courseRepository) {
        this.courseRepository = courseRepository;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listCatalogue(
            UUID tenantId,
            CatalogueFilter filter,
            int limit) {
        int cap = Math.min(Math.max(limit, 1), 500);
        String status = filter.status() != null ? filter.status() : "PUBLISHED";
        PageRequest page = PageRequest.of(0, cap);

        List<CourseEntity> rows;
        if ("ALL".equalsIgnoreCase(status)) {
            rows = courseRepository.findByTenantId(tenantId, page);
        } else if (filter.category() != null) {
            rows = courseRepository.findByTenantIdAndStatusAndCategory(tenantId, status, filter.category(), page);
        } else if (filter.level() != null) {
            rows = courseRepository.findByTenantIdAndStatusAndLevel(tenantId, status, filter.level(), page);
        } else if (filter.cpdEligible() != null) {
            rows = courseRepository.findByTenantIdAndStatusAndCpdEligible(tenantId, status, filter.cpdEligible(), page);
        } else if (filter.mandatory() != null) {
            rows = courseRepository.findByTenantIdAndStatusAndMandatory(tenantId, status, filter.mandatory(), page);
        } else if (filter.language() != null) {
            rows = courseRepository.findByTenantIdAndStatusAndLanguage(tenantId, status, filter.language(), page);
        } else {
            rows = courseRepository.findByTenantIdAndStatus(tenantId, status, page);
        }

        return rows.stream().map(FundoCatalogService::toView).toList();
    }

    @Transactional(readOnly = true)
    public Optional<Map<String, Object>> getCourse(UUID tenantId, UUID courseId) {
        return courseRepository.findById(courseId)
                .filter(c -> c.getTenantId().equals(tenantId))
                .map(FundoCatalogService::toView);
    }

    public static Map<String, Object> toView(CourseEntity c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId().toString());
        m.put("code", c.getCode());
        m.put("title", c.getTitle());
        m.put("description", c.getDescription());
        m.put("category", c.getCategory());
        m.put("level", c.getLevel());
        m.put("status", c.getStatus());
        m.put("language", c.getLanguage());
        m.put("estimatedDurationMinutes", c.getEstimatedDurationMinutes());
        m.put("mandatory", c.isMandatory());
        m.put("cpdEligible", c.isCpdEligible());
        m.put("cpdPoints", c.getCpdPoints());
        // V031/V032. Always projected, including when null — a learner-facing surface that
        // silently omits a deadline is indistinguishable from one that genuinely has none.
        m.put("dueDateType", c.getDueDateType());
        m.put("dueDate", c.getDueDate());
        m.put("dueDateDaysFromEnrollment", c.getDueDateDaysFromEnrollment());
        m.put("audienceType", c.getAudienceType());
        m.put("audienceRoles", c.getAudienceRoles());
        m.put("version", c.getVersion());
        return m;
    }

    /** Catalogue filter inputs (all nullable). */
    public record CatalogueFilter(
            String status,
            String category,
            String level,
            Boolean cpdEligible,
            Boolean mandatory,
            String language) {}
}
