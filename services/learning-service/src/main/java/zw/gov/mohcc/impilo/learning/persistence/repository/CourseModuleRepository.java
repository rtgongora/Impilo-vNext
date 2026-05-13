package zw.gov.mohcc.impilo.learning.persistence.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.learning.persistence.entity.CourseModuleEntity;

public interface CourseModuleRepository extends JpaRepository<CourseModuleEntity, UUID> {
    List<CourseModuleEntity> findByCourseIdOrderBySequenceNoAsc(UUID courseId);
}
