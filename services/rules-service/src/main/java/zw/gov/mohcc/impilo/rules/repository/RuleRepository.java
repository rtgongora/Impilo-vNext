package zw.gov.mohcc.impilo.rules.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.rules.domain.RuleEntity;

import java.util.List;

@Repository
public interface RuleRepository extends JpaRepository<RuleEntity, String> {

    List<RuleEntity> findByTenantIdAndEnabledTrue(String tenantId);

    List<RuleEntity> findByTenantId(String tenantId);
}
