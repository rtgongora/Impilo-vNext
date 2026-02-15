package zw.gov.mohcc.impilo.rules.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.rules.domain.EvaluationAuditEntity;

import java.util.List;

@Repository
public interface EvaluationAuditRepository extends JpaRepository<EvaluationAuditEntity, String> {

    List<EvaluationAuditEntity> findByRuleKeyAndTenantId(String ruleKey, String tenantId);
}
