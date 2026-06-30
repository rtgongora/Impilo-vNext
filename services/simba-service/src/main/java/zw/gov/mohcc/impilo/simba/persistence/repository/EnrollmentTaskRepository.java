package zw.gov.mohcc.impilo.simba.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.simba.persistence.entity.EnrollmentTaskEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EnrollmentTaskRepository extends JpaRepository<EnrollmentTaskEntity, Long> {

    List<EnrollmentTaskEntity> findByEnrollmentIdOrderByCreatedAtAsc(UUID enrollmentId);

    Optional<EnrollmentTaskEntity> findByEnrollmentTaskIdAndTenantId(UUID enrollmentTaskId, UUID tenantId);

    long countByEnrollmentId(UUID enrollmentId);

    long countByEnrollmentIdAndStatus(UUID enrollmentId, String status);
}
