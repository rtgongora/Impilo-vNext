package zw.gov.mohcc.impilo.learning.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.learning.persistence.entity.CourseProgressEntity;

public interface CourseProgressRepository extends JpaRepository<CourseProgressEntity, UUID> {

    /** Lesson-scoped row. */
    Optional<CourseProgressEntity> findByEnrolmentIdAndLessonId(UUID enrolmentId, UUID lessonId);

    /** Module-scoped row (lesson_id IS NULL, module_id IS NOT NULL). */
    Optional<CourseProgressEntity> findByEnrolmentIdAndModuleIdAndLessonIdIsNull(
            UUID enrolmentId, UUID moduleId);

    /** Course-scoped aggregate row (both module_id and lesson_id IS NULL). */
    Optional<CourseProgressEntity> findByEnrolmentIdAndModuleIdIsNullAndLessonIdIsNull(UUID enrolmentId);

    List<CourseProgressEntity> findByEnrolmentIdOrderByCreatedAtAsc(UUID enrolmentId);

    List<CourseProgressEntity> findByTenantIdAndSubjectTypeAndSubjectId(
            UUID tenantId, String subjectType, String subjectId);
}
