package zw.gov.mohcc.impilo.datagovernance.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import zw.gov.mohcc.impilo.datagovernance.domain.GovernanceRuleEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GovernanceRuleRepository extends JpaRepository<GovernanceRuleEntity, UUID> {
    List<GovernanceRuleEntity> findByTenantIdAndActiveTrue(UUID tenantId);
    Optional<GovernanceRuleEntity> findByTenantIdAndRuleId(UUID tenantId, UUID ruleId);

    @Query("SELECT r FROM GovernanceRuleEntity r WHERE r.tenantId = :tenantId AND r.active = true " +
           "AND r.resourcePattern = :resource AND r.action = :action ORDER BY r.priority ASC")
    List<GovernanceRuleEntity> findMatchingRules(UUID tenantId, String resource, String action);
}
