package zw.gov.mohcc.impilo.coverage.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.coverage.domain.EmployerEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmployerRepository extends JpaRepository<EmployerEntity, UUID> {
    List<EmployerEntity> findByTenantId(UUID tenantId);
    Optional<EmployerEntity> findByIdAndTenantId(UUID id, UUID tenantId);
    Optional<EmployerEntity> findByTenantIdAndEmployerCode(UUID tenantId, String employerCode);
}
