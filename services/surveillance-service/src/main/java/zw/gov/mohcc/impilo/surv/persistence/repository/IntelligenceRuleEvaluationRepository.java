package zw.gov.mohcc.impilo.surv.persistence.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.surv.persistence.entity.IntelligenceRuleEvaluationEntity;

public interface IntelligenceRuleEvaluationRepository extends JpaRepository<IntelligenceRuleEvaluationEntity, Long> {
    List<IntelligenceRuleEvaluationEntity> findByTenantIdOrderByIdDesc(UUID tenantId);
}
