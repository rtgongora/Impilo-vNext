package zw.gov.mohcc.impilo.learning.fundo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.learning.persistence.entity.CourseEntity;
import zw.gov.mohcc.impilo.learning.persistence.repository.CourseRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.LearningSpaceRepository;

/**
 * Delegated learning administration (C3): bind content to a learning space and serve
 * space-scoped views so a space admin manages only their academy's content. The access
 * decision (who may administer a space) is Tshepo policy — see
 * docs/policy/fundo-space-admin-access.md (spec + queued rego).
 */
@Service
public class FundoSpaceAdminService {

    private final CourseRepository courseRepository;
    private final LearningSpaceRepository spaceRepository;

    public FundoSpaceAdminService(CourseRepository courseRepository, LearningSpaceRepository spaceRepository) {
        this.courseRepository = courseRepository;
        this.spaceRepository = spaceRepository;
    }

    /** Bind a course to a learning space (or pass null to return it to national scope). */
    @Transactional
    public Map<String, Object> assignCourseToSpace(UUID tenantId, String courseId, String learningSpaceId) {
        CourseEntity course = courseRepository.findByTenantIdAndId(tenantId, UUID.fromString(courseId))
                .orElseThrow(() -> new IllegalArgumentException("course not found"));
        UUID space = (learningSpaceId == null || learningSpaceId.isBlank()) ? null : UUID.fromString(learningSpaceId);
        if (space != null) {
            spaceRepository.findByTenantIdAndId(tenantId, space)
                    .orElseThrow(() -> new IllegalArgumentException("learning space not found"));
        }
        course.setLearningSpaceId(space);
        courseRepository.save(course);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("courseId", course.getId().toString());
        m.put("learningSpaceId", space == null ? null : space.toString());
        return m;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listSpaceCourses(UUID tenantId, String learningSpaceId, int limit) {
        List<Map<String, Object>> items = courseRepository
                .findByTenantIdAndLearningSpaceId(tenantId, UUID.fromString(learningSpaceId), PageRequest.of(0, limit))
                .stream().map(FundoSpaceAdminService::courseView).toList();
        return Map.of("learningSpaceId", learningSpaceId, "items", items, "limit", limit);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> spaceSummary(UUID tenantId, String learningSpaceId) {
        UUID space = UUID.fromString(learningSpaceId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("learningSpaceId", learningSpaceId);
        out.put("courseCount", courseRepository.countByTenantIdAndLearningSpaceId(tenantId, space));
        spaceRepository.findByTenantIdAndId(tenantId, space).ifPresent(s -> {
            out.put("name", s.getName());
            out.put("spaceKind", s.getSpaceKind());
            out.put("providerId", s.getProviderId().toString());
            out.put("status", s.getStatus());
        });
        return out;
    }

    private static Map<String, Object> courseView(CourseEntity c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId().toString());
        m.put("code", c.getCode());
        m.put("title", c.getTitle());
        m.put("status", c.getStatus());
        m.put("learningSpaceId", c.getLearningSpaceId() == null ? null : c.getLearningSpaceId().toString());
        return m;
    }
}
