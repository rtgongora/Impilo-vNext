package zw.gov.mohcc.impilo.surv.persistence.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.surv.persistence.entity.IntelligenceRuleConditionEntity;

public interface IntelligenceRuleConditionRepository extends JpaRepository<IntelligenceRuleConditionEntity, Long> {
    List<IntelligenceRuleConditionEntity> findByTenantIdAndRuleIdAndActiveTrue(UUID tenantId, Long ruleId);
}
