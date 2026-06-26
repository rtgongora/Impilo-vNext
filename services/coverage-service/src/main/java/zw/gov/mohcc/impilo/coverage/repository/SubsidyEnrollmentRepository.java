package zw.gov.mohcc.impilo.coverage.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.coverage.domain.SubsidyEnrollmentEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface SubsidyEnrollmentRepository extends JpaRepository<SubsidyEnrollmentEntity, UUID> {

    List<SubsidyEnrollmentEntity> findByTenantIdAndClientIdOrderByCreatedAtDesc(UUID tenantId, String clientId);

    List<SubsidyEnrollmentEntity> findByTenantIdAndClientIdAndStatusOrderByCreatedAtDesc(
            UUID tenantId, String clientId, String status);
}
