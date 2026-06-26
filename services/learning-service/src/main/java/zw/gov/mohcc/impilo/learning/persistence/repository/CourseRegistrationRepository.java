package zw.gov.mohcc.impilo.learning.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.learning.persistence.entity.CourseRegistrationEntity;

public interface CourseRegistrationRepository extends JpaRepository<CourseRegistrationEntity, UUID> {

    Optional<CourseRegistrationEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    List<CourseRegistrationEntity> findByTenantIdAndStudentProfileIdOrderByCreatedAtDesc(UUID tenantId, UUID studentProfileId);
}
