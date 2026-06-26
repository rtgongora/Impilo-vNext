package zw.gov.mohcc.impilo.rito.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.rito.persistence.entity.EscalationRuleEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EscalationRuleRepository extends JpaRepository<EscalationRuleEntity, UUID> {

    Optional<EscalationRuleEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    List<EscalationRuleEntity> findByTenantIdAndActiveTrue(UUID tenantId);

    boolean existsByTenantIdAndRuleKey(UUID tenantId, String ruleKey);
}
