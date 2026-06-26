package zw.gov.mohcc.impilo.learning.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.learning.persistence.entity.CourseEntity;

public interface CourseRepository extends JpaRepository<CourseEntity, UUID> {

    Optional<CourseEntity> findByTenantIdAndCode(UUID tenantId, String code);

    List<CourseEntity> findByTenantId(UUID tenantId, Pageable pageable);

    List<CourseEntity> findByTenantIdAndStatus(UUID tenantId, String status, Pageable pageable);

    List<CourseEntity> findByTenantIdAndStatusAndCategory(
            UUID tenantId, String status, String category, Pageable pageable);

    List<CourseEntity> findByTenantIdAndStatusAndLevel(
            UUID tenantId, String status, String level, Pageable pageable);

    List<CourseEntity> findByTenantIdAndStatusAndCpdEligible(
            UUID tenantId, String status, boolean cpdEligible, Pageable pageable);

    List<CourseEntity> findByTenantIdAndStatusAndMandatory(
            UUID tenantId, String status, boolean mandatory, Pageable pageable);

    List<CourseEntity> findByTenantIdAndStatusAndLanguage(
            UUID tenantId, String status, String language, Pageable pageable);

    /** Space-scoped catalogue for delegated/academy administration (V022). */
    List<CourseEntity> findByTenantIdAndLearningSpaceId(UUID tenantId, UUID learningSpaceId, Pageable pageable);
}
