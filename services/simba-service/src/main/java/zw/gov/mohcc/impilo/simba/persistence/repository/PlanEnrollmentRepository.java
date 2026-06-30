package zw.gov.mohcc.impilo.simba.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.simba.persistence.entity.PlanEnrollmentEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlanEnrollmentRepository extends JpaRepository<PlanEnrollmentEntity, Long> {

    List<PlanEnrollmentEntity> findByTenantIdAndPersonCpidOrderByCreatedAtDesc(UUID tenantId, String personCpid);

    Optional<PlanEnrollmentEntity> findByEnrollmentIdAndTenantId(UUID enrollmentId, UUID tenantId);

    Optional<PlanEnrollmentEntity> findByPlanIdAndPersonCpid(UUID planId, String personCpid);
}
