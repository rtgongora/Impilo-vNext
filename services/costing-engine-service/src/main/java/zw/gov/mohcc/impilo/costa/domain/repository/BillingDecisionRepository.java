package zw.gov.mohcc.impilo.costa.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.costa.domain.entity.BillingDecisionEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BillingDecisionRepository extends JpaRepository<BillingDecisionEntity, UUID> {

    Optional<BillingDecisionEntity> findByBillingDecisionIdAndTenantId(UUID id, UUID tenantId);

    Optional<BillingDecisionEntity> findFirstByCostEstimateIdAndTenantIdOrderByCreatedAtDesc(UUID costEstimateId, UUID tenantId);
}
