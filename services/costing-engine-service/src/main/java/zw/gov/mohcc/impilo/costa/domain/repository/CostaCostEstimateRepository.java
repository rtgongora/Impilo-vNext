package zw.gov.mohcc.impilo.costa.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.costa.domain.entity.CostaCostEstimateEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface CostaCostEstimateRepository extends JpaRepository<CostaCostEstimateEntity, UUID> {

    List<CostaCostEstimateEntity> findTop20ByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
