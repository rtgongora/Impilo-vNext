package zw.gov.mohcc.impilo.learning.fundo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.learning.persistence.entity.CourseEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.CourseLessonEntity;
import zw.gov.mohcc.impilo.learning.persistence.entity.CourseModuleEntity;
import zw.gov.mohcc.impilo.learning.persistence.repository.CourseLessonRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.CourseModuleRepository;
import zw.gov.mohcc.impilo.learning.persistence.repository.CourseRepository;

/** Returns a course with its ordered modules and lessons (Phase 5B). */
@Service
public class FundoCourseStructureService {

    private final CourseRepository courseRepository;
    private final CourseModuleRepository moduleRepository;
    private final CourseLessonRepository lessonRepository;

    public FundoCourseStructureService(
            CourseRepository courseRepository,
            CourseModuleRepository moduleRepository,
            CourseLessonRepository lessonRepository) {
        this.courseRepository = courseRepository;
        this.moduleRepository = moduleRepository;
        this.lessonRepository = lessonRepository;
    }

    @Transactional(readOnly = true)
    public Optional<Map<String, Object>> getStructure(UUID tenantId, UUID courseId) {
        Optional<CourseEntity> course = courseRepository.findById(courseId)
                .filter(c -> c.getTenantId().equals(tenantId));
        if (course.isEmpty()) return Optional.empty();

        List<CourseModuleEntity> modules = moduleRepository.findByCourseIdOrderBySequenceNoAsc(courseId);
        Map<UUID, List<Map<String, Object>>> lessonsByModule = new HashMap<>();
        if (!modules.isEmpty()) {
            List<UUID> moduleIds = modules.stream().map(CourseModuleEntity::getId).toList();
            List<CourseLessonEntity> lessons = lessonRepository.findByModuleIdInOrderBySequenceNoAsc(moduleIds);
            for (CourseLessonEntity l : lessons) {
                lessonsByModule.computeIfAbsent(l.getModuleId(), k -> new ArrayList<>()).add(toLessonView(l));
            }
        }

        Map<String, Object> view = new LinkedHashMap<>(FundoCatalogService.toView(course.get()));
        List<Map<String, Object>> moduleViews = new ArrayList<>();
        for (CourseModuleEntity m : modules) {
            Map<String, Object> mv = new LinkedHashMap<>();
            mv.put("id", m.getId().toString());
            mv.put("title", m.getTitle());
            mv.put("description", m.getDescription());
            mv.put("sequence", m.getSequenceNo());
            mv.put("status", m.getStatus());
            mv.put("lessons", lessonsByModule.getOrDefault(m.getId(), List.of()));
            moduleViews.add(mv);
        }
        view.put("modules", moduleViews);
        return Optional.of(view);
    }

    public static Map<String, Object> toLessonView(CourseLessonEntity l) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", l.getId().toString());
        v.put("title", l.getTitle());
        v.put("contentType", l.getContentType());
        v.put("contentBody", l.getContentBody());
        v.put("contentRef", l.getContentRef());
        v.put("estimatedDurationMinutes", l.getEstimatedDurationMinutes());
        v.put("sequence", l.getSequenceNo());
        v.put("required", l.isRequired());
        v.put("status", l.getStatus());
        return v;
    }
}
