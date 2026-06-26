package zw.gov.mohcc.impilo.patientsafety.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.patientsafety.domain.SafetyCaseEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SafetyCaseRepository extends JpaRepository<SafetyCaseEntity, UUID> {

    Optional<SafetyCaseEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<SafetyCaseEntity> findByReportId(UUID reportId);

    List<SafetyCaseEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<SafetyCaseEntity> findByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, String status);

    List<SafetyCaseEntity> findByTenantIdAndPriorityOrderByCreatedAtDesc(UUID tenantId, String priority);

    List<SafetyCaseEntity> findByTenantIdAndStatusInOrderByCreatedAtDesc(UUID tenantId, List<String> statuses);

    long countByTenantIdAndStatus(UUID tenantId, String status);

    long countByTenantIdAndCaseReferenceStartingWith(UUID tenantId, String prefix);
}
