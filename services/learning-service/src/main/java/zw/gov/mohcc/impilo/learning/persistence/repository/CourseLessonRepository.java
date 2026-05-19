package zw.gov.mohcc.impilo.learning.persistence.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.learning.persistence.entity.CourseLessonEntity;

public interface CourseLessonRepository extends JpaRepository<CourseLessonEntity, UUID> {
    List<CourseLessonEntity> findByModuleIdOrderBySequenceNoAsc(UUID moduleId);

    List<CourseLessonEntity> findByModuleIdInOrderBySequenceNoAsc(List<UUID> moduleIds);
}
