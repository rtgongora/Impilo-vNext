package zw.gov.mohcc.impilo.coverage.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.coverage.domain.CoveragePlanEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CoveragePlanRepository extends JpaRepository<CoveragePlanEntity, UUID> {

    List<CoveragePlanEntity> findByTenantIdAndStatus(UUID tenantId, String status);

    Optional<CoveragePlanEntity> findFirstByTenantIdAndPlanCode(UUID tenantId, String planCode);
}
