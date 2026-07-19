package zw.gov.mohcc.impilo.coverage.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.coverage.domain.EmployerRosterBatchEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmployerRosterBatchRepository extends JpaRepository<EmployerRosterBatchEntity, UUID> {
    List<EmployerRosterBatchEntity> findByTenantIdAndEmployerId(UUID tenantId, UUID employerId);
    Optional<EmployerRosterBatchEntity> findByIdAndTenantId(UUID id, UUID tenantId);
}
