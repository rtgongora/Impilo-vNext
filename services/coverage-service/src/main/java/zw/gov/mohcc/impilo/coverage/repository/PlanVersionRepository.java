package zw.gov.mohcc.impilo.coverage.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.coverage.domain.PlanVersionEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlanVersionRepository extends JpaRepository<PlanVersionEntity, UUID> {
    List<PlanVersionEntity> findByTenantIdAndProductRef(UUID tenantId, UUID productRef);
    Optional<PlanVersionEntity> findByIdAndTenantId(UUID id, UUID tenantId);
    long countByTenantIdAndProductRef(UUID tenantId, UUID productRef);
}
